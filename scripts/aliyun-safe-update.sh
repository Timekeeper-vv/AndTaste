#!/usr/bin/env bash
set -Eeuo pipefail

# Safe update for an existing ECS deployment. It deliberately keeps the
# database backup, runtime assets, and previous JAR outside the Git checkout.

APP_DIR="${APP_DIR:-/opt/smart_pig}"
BRANCH="${BRANCH:-main}"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/smart_pig-backups}"
CANDIDATE_PORT="${CANDIDATE_PORT:-18080}"
HEALTH_PATH="${HEALTH_PATH:-/actuator/health/readiness}"
APP_NAME="smart-pig"
SERVICE_UNIT="/etc/systemd/system/${APP_NAME}.service"

info(){ echo -e "\033[1;34m[INFO]\033[0m $*"; }
ok(){ echo -e "\033[1;32m[OK]\033[0m $*"; }
warn(){ echo -e "\033[1;33m[WARN]\033[0m $*"; }
die(){ echo -e "\033[1;31m[ERR]\033[0m $*" >&2; exit 1; }
need(){ command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"; }
run_root(){ if [ "$(id -u)" = 0 ]; then "$@"; else sudo "$@"; fi; }

[ -d "$APP_DIR/.git" ] || die "APP_DIR 不是 Git 仓库：$APP_DIR"
[ -f "$APP_DIR/.env" ] || die "找不到 $APP_DIR/.env；不会在未加载生产配置时更新"

for command in git curl gzip mysqldump mysql tar java; do need "$command"; done
need systemctl

ROOT_DIR="$(cd "$APP_DIR" && pwd)"
ENV_FILE="$ROOT_DIR/.env"
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

APP_PORT="${APP_PORT:-8080}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-shixun}"
DB_USER="${DB_USER:-smart_pig}"
DB_PASSWORD="${DB_PASSWORD:-}"
MYSQL_ADMIN_USER="${MYSQL_ADMIN_USER:-root}"
MYSQL_ADMIN_PASSWORD="${MYSQL_ADMIN_PASSWORD:-}"
BACKEND_DIR="$ROOT_DIR/shixun"
STATIC_DIR="$BACKEND_DIR/src/main/resources/static"
STATIC_REL="shixun/src/main/resources/static"
CREATIVE_ASSET_PRIVATE_ROOT="${CREATIVE_ASSET_PRIVATE_ROOT:-$BACKEND_DIR/data/creative-assets}"
OLD_JAR_PATH=""
OLD_HEAD=""
BACKUP_DIR=""
DB_BACKUP=""
OLD_JAR_BACKUP=""
OLD_SERVICE_BACKUP=""
CANDIDATE_PID=""
BUILD_STARTED=0
NO_UPDATE=0

wait_http(){
  local url="$1" attempts="${2:-60}"
  for ((i=1; i<=attempts; i++)); do
    if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  return 1
}

wait_app_health(){
  local port="$1"
  wait_http "http://127.0.0.1:${port}${HEALTH_PATH}" 60 || wait_http "http://127.0.0.1:${port}/" 10
}

find_current_jar(){
  find "$BACKEND_DIR/target" -maxdepth 1 -type f -name '*.jar' \
    ! -name '*.original' ! -name '*sources.jar' -print -quit 2>/dev/null || true
}

dump_database(){
  [ -n "$DB_PASSWORD" ] || die "DB_PASSWORD 为空，已停止更新"
  [[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || die "DB_NAME 格式不合法"
  DB_BACKUP="$BACKUP_DIR/${DB_NAME}.sql.gz"
  info "备份 MySQL 数据库：$DB_NAME"
  if [[ "$DB_HOST" == "127.0.0.1" || "$DB_HOST" == "localhost" ]] && \
     [ -z "$MYSQL_ADMIN_PASSWORD" ] && run_root mysql -u"$MYSQL_ADMIN_USER" -e 'SELECT 1' >/dev/null 2>&1; then
    run_root mysqldump --single-transaction --quick --routines --events --triggers \
      --hex-blob --no-tablespaces --set-gtid-purged=OFF -u"$MYSQL_ADMIN_USER" "$DB_NAME" \
      | gzip -9 > "$DB_BACKUP"
  else
    [ -n "$MYSQL_ADMIN_PASSWORD" ] || die "远程 MySQL/RDS 必须设置 MYSQL_ADMIN_PASSWORD"
    MYSQL_PWD="$MYSQL_ADMIN_PASSWORD" mysqldump -h"$DB_HOST" -P"$DB_PORT" \
      -u"$MYSQL_ADMIN_USER" --single-transaction --quick --routines --events --triggers \
      --hex-blob --no-tablespaces --set-gtid-purged=OFF "$DB_NAME" | gzip -9 > "$DB_BACKUP"
  fi
  [ -s "$DB_BACKUP" ] && gzip -t "$DB_BACKUP" || die "数据库备份为空或校验失败：$DB_BACKUP"
  chmod 600 "$DB_BACKUP"
  ok "数据库备份已校验：$DB_BACKUP"
}

backup_runtime_data(){
  info "备份 .env、应用配置和运行时资产"
  cp -p "$ENV_FILE" "$BACKUP_DIR/env"
  if [ -f "$BACKEND_DIR/application-local.properties" ]; then
    cp -p "$BACKEND_DIR/application-local.properties" "$BACKUP_DIR/application-local.properties"
  fi
  if [ -f "$SERVICE_UNIT" ]; then
    run_root cp -p "$SERVICE_UNIT" "$BACKUP_DIR/smart-pig.service"
    OLD_SERVICE_BACKUP="$BACKUP_DIR/smart-pig.service"
  fi
  if [ -d "$CREATIVE_ASSET_PRIVATE_ROOT" ]; then
    local parent name
    parent="$(dirname "$CREATIVE_ASSET_PRIVATE_ROOT")"
    name="$(basename "$CREATIVE_ASSET_PRIVATE_ROOT")"
    tar -C "$parent" -czf "$BACKUP_DIR/creative-assets.tgz" "$name"
  fi
  if [ -d "$STATIC_DIR/generated" ]; then
    tar -C "$STATIC_DIR" -czf "$BACKUP_DIR/static-generated.tgz" generated
  fi
  if [ -d "$STATIC_DIR/uploads" ]; then
    tar -C "$STATIC_DIR" -czf "$BACKUP_DIR/static-uploads.tgz" uploads
  fi
  if [ -d "$STATIC_DIR" ]; then
    # Build replaces only these web-bundle files. generated/uploads stay live.
    tar -C "$STATIC_DIR" --exclude='./generated' --exclude='./uploads' \
      -czf "$BACKUP_DIR/static-bundle.tgz" .
  fi
  chmod 600 "$BACKUP_DIR"/* 2>/dev/null || true
}

prepare_backup(){
  umask 077
  mkdir -p "$BACKUP_ROOT"
  BACKUP_DIR="$BACKUP_ROOT/$(date +%Y%m%d-%H%M%S)"
  mkdir "$BACKUP_DIR"
  OLD_HEAD="$(git -C "$ROOT_DIR" rev-parse HEAD)"
  OLD_JAR_PATH="$(find_current_jar)"
  [ -n "$OLD_JAR_PATH" ] || die "找不到当前运行 JAR；请先完成一次 production 部署"
  cp -p "$OLD_JAR_PATH" "$BACKUP_DIR/$(basename "$OLD_JAR_PATH")"
  OLD_JAR_BACKUP="$BACKUP_DIR/$(basename "$OLD_JAR_PATH")"
  git -C "$ROOT_DIR" status --short > "$BACKUP_DIR/git-status-before.txt"
  printf '%s\n' "$OLD_HEAD" > "$BACKUP_DIR/git-head-before.txt"
  dump_database
  backup_runtime_data
}

normalize_generated_static_changes(){
  local dirty line path static_dirty=0 remaining=""
  dirty="$(git -C "$ROOT_DIR" status --porcelain --untracked-files=all)"
  [ -n "$dirty" ] || return 0
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    path="${line:3}"
    path="${path#\"}"
    path="${path%\"}"
    case "$path" in
      "$STATIC_REL"|"$STATIC_REL"/*) static_dirty=1 ;;
      .env.*|shixun/data|shixun/data/*|secrets|secrets/*|shixun-vue/public/*.txt|nohup.out|loongcollector.sh|openclaw_installer.sh|tmp|tmp/*|et\ -a)
        warn "保留服务器本地运行文件：$path"
        ;;
      *) die "服务器工作区有非构建产物改动，已停止：$line" ;;
    esac
  done <<< "$dirty"
  if [ "$static_dirty" -eq 1 ]; then
    [ -f "$BACKUP_DIR/static-bundle.tgz" ] || die "静态资源备份缺失，拒绝清理工作区"
    warn "检测到前端构建产物改动；已备份后恢复 Git 静态目录，保留 generated/uploads"
    find "$STATIC_DIR" -mindepth 1 -maxdepth 1 \
      ! -name generated ! -name uploads -exec rm -rf -- {} +
    git -C "$ROOT_DIR" restore --source=HEAD --staged --worktree -- "$STATIC_REL"
  fi

  dirty="$(git -C "$ROOT_DIR" status --porcelain --untracked-files=all)"
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    path="${line:3}"
    path="${path#\"}"
    path="${path%\"}"
    case "$path" in
      .env.*|shixun/data|shixun/data/*|secrets|secrets/*|shixun-vue/public/*.txt|nohup.out|loongcollector.sh|openclaw_installer.sh|tmp|tmp/*|et\ -a) ;;
      *) remaining+="$line"$'\n' ;;
    esac
  done <<< "$dirty"
  [ -z "$remaining" ] || die "服务器工作区有未提交改动，已停止；请先保存或清理后再更新：${remaining//$'\n'/ }"
}

update_code(){
  normalize_generated_static_changes
  info "获取 origin/$BRANCH，并仅允许快进更新"
  git -C "$ROOT_DIR" fetch origin "$BRANCH"
  local remote_head
  remote_head="$(git -C "$ROOT_DIR" rev-parse "origin/$BRANCH")"
  git -C "$ROOT_DIR" merge-base --is-ancestor "$OLD_HEAD" "$remote_head" || \
    die "远端不是当前版本的快进更新，未修改服务器代码；请人工检查分支"
  if [ "$(git -C "$ROOT_DIR" branch --show-current)" != "$BRANCH" ]; then
    if git -C "$ROOT_DIR" show-ref --verify --quiet "refs/heads/$BRANCH"; then
      git -C "$ROOT_DIR" switch "$BRANCH"
    else
      git -C "$ROOT_DIR" switch -c "$BRANCH" --track "origin/$BRANCH"
    fi
  fi
  git -C "$ROOT_DIR" pull --ff-only origin "$BRANCH"
  if [ "$(git -C "$ROOT_DIR" rev-parse HEAD)" = "$OLD_HEAD" ]; then
    NO_UPDATE=1
    printf '%s\n' "$OLD_HEAD" > "$BACKUP_DIR/git-head-after.txt"
    info "origin/$BRANCH 没有新提交，不重启线上服务"
    return
  fi
  printf '%s\n' "$(git -C "$ROOT_DIR" rev-parse HEAD)" > "$BACKUP_DIR/git-head-after.txt"
  ok "代码已快进更新：$OLD_HEAD -> $(git -C "$ROOT_DIR" rev-parse --short HEAD)"
}

start_candidate(){
  local candidate_log="$BACKUP_DIR/candidate.log"
  info "在 127.0.0.1:$CANDIDATE_PORT 启动候选版本做健康检查"
  cd "$BACKEND_DIR"
  nohup env SERVER_ADDRESS=127.0.0.1 \
    java ${JAVA_OPTS:--Xms512m -Xmx1536m -XX:+UseG1GC -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai} \
    -jar "$NEW_JAR_PATH" --server.port="$CANDIDATE_PORT" --server.address=127.0.0.1 \
    --spring.task.scheduling.enabled=false > "$candidate_log" 2>&1 &
  CANDIDATE_PID="$!"
  if ! wait_app_health "$CANDIDATE_PORT"; then
    tail -100 "$candidate_log" >&2 || true
    die "候选版本健康检查失败；旧服务仍保持运行"
  fi
  ok "候选版本健康检查通过"
}

stop_candidate(){
  [ -n "$CANDIDATE_PID" ] || return 0
  kill "$CANDIDATE_PID" 2>/dev/null || true
  for _ in {1..20}; do
    ps -p "$CANDIDATE_PID" >/dev/null 2>&1 || break
    sleep 1
  done
  kill -9 "$CANDIDATE_PID" 2>/dev/null || true
  CANDIDATE_PID=""
}

restore_old_service(){
  [ -n "$OLD_JAR_BACKUP" ] || return 0
  warn "正在恢复旧 JAR 和 systemd 服务；不会回滚数据库"
  run_root systemctl stop "$APP_NAME" >/dev/null 2>&1 || true
  run_root cp -p "$OLD_JAR_BACKUP" "$OLD_JAR_PATH"
  if [ -n "$OLD_SERVICE_BACKUP" ] && [ -f "$OLD_SERVICE_BACKUP" ]; then
    run_root cp -p "$OLD_SERVICE_BACKUP" "$SERVICE_UNIT"
    run_root systemctl daemon-reload || true
  fi
  run_root systemctl start "$APP_NAME" >/dev/null 2>&1 || true
  if wait_app_health "$APP_PORT"; then
    ok "旧版本已恢复并通过健康检查"
  else
    warn "旧版本已尝试恢复，但健康检查未通过；请查看 journalctl -u $APP_NAME"
  fi
}

on_exit(){
  local rc="$?"
  trap - EXIT
  set +e
  stop_candidate
  if [ "$rc" -ne 0 ]; then
    if [ "$BUILD_STARTED" -eq 1 ]; then
      restore_old_service
    fi
    if [ -n "$BACKUP_DIR" ]; then
      warn "更新失败；数据库和运行文件备份保留在：$BACKUP_DIR"
      warn "不要直接导入数据库备份覆盖线上库；如确需恢复，请先停止写入并人工确认备份时间点"
    fi
  fi
  if [ "$rc" -eq 0 ]; then
    ok "安全更新完成；备份保留在：$BACKUP_DIR"
  fi
  exit "$rc"
}
trap on_exit EXIT

[ "$CANDIDATE_PORT" != "$APP_PORT" ] || die "CANDIDATE_PORT 不能和 APP_PORT 相同"
case "$BACKUP_ROOT" in
  "$ROOT_DIR"|"$ROOT_DIR"/*) die "BACKUP_ROOT 不能放在代码仓库内：$BACKUP_ROOT" ;;
esac
run_root systemctl cat "$APP_NAME.service" >/dev/null 2>&1 || die "找不到 $APP_NAME.service；请先执行一次 production 部署"

prepare_backup
update_code
[ "$NO_UPDATE" -eq 0 ] || exit 0

info "初始化数据库账号和非破坏性迁移准备"
bash "$ROOT_DIR/scripts/aliyun-start.sh" init-db

BUILD_STARTED=1
bash "$ROOT_DIR/scripts/aliyun-start.sh" build
NEW_JAR_PATH="$(find_current_jar)"
[ -n "$NEW_JAR_PATH" ] || die "新版本构建后找不到 JAR"
start_candidate
stop_candidate

info "候选版本已验证，切换 systemd 正式服务"
bash "$ROOT_DIR/scripts/aliyun-start.sh" service
wait_app_health "$APP_PORT" || die "正式服务切换后健康检查失败"

printf '%s\n' "$(date -Is)" > "$BACKUP_DIR/success.txt"
ok "正式服务已运行在 127.0.0.1:$APP_PORT"
