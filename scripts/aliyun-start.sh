#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="smart-pig"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
if [ -f "$ENV_FILE" ]; then set -a; source "$ENV_FILE"; set +a; fi

APP_PORT="${APP_PORT:-8080}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1536m -XX:+UseG1GC -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai}"
BACKEND_DIR="$ROOT_DIR/shixun"; FRONTEND_DIR="$ROOT_DIR/shixun-vue"
RUN_DIR="$ROOT_DIR/runtime"; LOG_DIR="$ROOT_DIR/logs"; PID_FILE="$RUN_DIR/$APP_NAME.pid"; APP_LOG="$LOG_DIR/$APP_NAME.log"
DB_HOST="${DB_HOST:-127.0.0.1}"; DB_PORT="${DB_PORT:-3306}"; DB_NAME="${DB_NAME:-shixun}"; DB_USER="${DB_USER:-smart_pig}"
DB_PASSWORD="${DB_PASSWORD:-ChangeMe_123456}"; MYSQL_ADMIN_USER="${MYSQL_ADMIN_USER:-root}"; MYSQL_ADMIN_PASSWORD="${MYSQL_ADMIN_PASSWORD:-}"
SILICONFLOW_API_KEY="${SILICONFLOW_API_KEY:-}"; SILICONFLOW_CHAT_MODEL="${SILICONFLOW_CHAT_MODEL:-Qwen/Qwen3-32B}"
SILICONFLOW_IMAGE_MODEL="${SILICONFLOW_IMAGE_MODEL:-Kwai-Kolors/Kolors}"; SILICONFLOW_IMAGE_EDIT_MODEL="${SILICONFLOW_IMAGE_EDIT_MODEL:-Qwen/Qwen-Image-Edit-2509}"
QWEN_API_KEY="${QWEN_API_KEY:-}"; TRIPO_API_KEY="${TRIPO_API_KEY:-}"; TRIPO_API_BASE_URL="${TRIPO_API_BASE_URL:-https://openapi.tripo3d.com/v3}"; TRIPO_CONVERT_BASE_URL="${TRIPO_CONVERT_BASE_URL:-https://api.tripo3d.ai/v2/openapi}"; TRIPO_MODEL_VERSION="${TRIPO_MODEL_VERSION:-v3.1-20260211}"
MODEL_CONVERT_PREFER_LOCAL="${MODEL_CONVERT_PREFER_LOCAL:-true}"; MODEL_CONVERT_BLENDER_COMMAND="${MODEL_CONVERT_BLENDER_COMMAND:-blender}"; MODEL_CONVERT_ASSIMP_COMMAND="${MODEL_CONVERT_ASSIMP_COMMAND:-assimp}"; MODEL_CONVERT_TIMEOUT_SECONDS="${MODEL_CONVERT_TIMEOUT_SECONDS:-300}"
REPLICATE_API_KEY="${REPLICATE_API_KEY:-}"; REPLICATE_API_BASE_URL="${REPLICATE_API_BASE_URL:-https://api.replicate.com/v1}"; REPLICATE_IMAGEN_MODEL="${REPLICATE_IMAGEN_MODEL:-google/imagen-4}"
MODAO_API_KEY="${MODAO_API_KEY:-}"; MODAO_DESIGN_URL="${MODAO_DESIGN_URL:-https://modao.cc/ai/design/spmrsxjgcyi6g0h1/6a5dd48151e5a21110c1697a}"; MODAO_MCP_URL="${MODAO_MCP_URL:-https://modao.cc/agent-py/ai/mcp}"; MODAO_CHROME_PATH="${MODAO_CHROME_PATH:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
KUAIDI100_CUSTOMER="${KUAIDI100_CUSTOMER:-}"; KUAIDI100_KEY="${KUAIDI100_KEY:-}"; KUAIDI100_CALLBACK_URL="${KUAIDI100_CALLBACK_URL:-}"; KUAIDI100_SALT="${KUAIDI100_SALT:-}"
JAR_FILE=""

info(){ echo -e "\033[1;34m[INFO]\033[0m $*"; }; ok(){ echo -e "\033[1;32m[OK]\033[0m $*"; }; warn(){ echo -e "\033[1;33m[WARN]\033[0m $*"; }; die(){ echo -e "\033[1;31m[ERR]\033[0m $*" >&2; exit 1; }
need(){ command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1，请先执行 install-deps"; }
run_root(){ if [ "$(id -u)" = 0 ]; then "$@"; else sudo "$@"; fi; }

install_deps(){
  info "安装 Java 17、Node.js 22、Git、MySQL、Nginx 等生产环境"
  if command -v apt-get >/dev/null 2>&1; then
    run_root apt-get update
    run_root apt-get install -y openjdk-17-jdk git curl ca-certificates gnupg unzip lsof nginx mysql-client
    run_root apt-get install -y blender assimp-utils || warn "Blender/assimp 安装失败；OBJ/STL本地转换需后续手动安装"
    if [ "${INSTALL_LOCAL_MYSQL:-true}" = "true" ]; then run_root apt-get install -y mysql-server; run_root systemctl enable --now mysql; fi
    if ! command -v node >/dev/null 2>&1 || [ "$(node -p 'Number(process.versions.node.split(`.`)[0])' 2>/dev/null || echo 0)" -lt 22 ]; then
      curl -fsSL https://deb.nodesource.com/setup_22.x | run_root bash -
      run_root apt-get install -y nodejs
    fi
  elif command -v dnf >/dev/null 2>&1 || command -v yum >/dev/null 2>&1; then
    PM=dnf; command -v dnf >/dev/null 2>&1 || PM=yum
    run_root "$PM" install -y java-17-openjdk java-17-openjdk-devel git curl ca-certificates unzip lsof nginx mysql
    run_root "$PM" install -y blender assimp || warn "Blender/assimp 安装失败；OBJ/STL本地转换需后续手动安装"
    if [ "${INSTALL_LOCAL_MYSQL:-true}" = "true" ]; then
      run_root "$PM" install -y mysql-server || run_root "$PM" install -y community-mysql-server
      run_root systemctl enable --now mysqld
    fi
    if ! command -v node >/dev/null 2>&1 || [ "$(node -p 'Number(process.versions.node.split(`.`)[0])' 2>/dev/null || echo 0)" -lt 22 ]; then
      curl -fsSL https://rpm.nodesource.com/setup_22.x | run_root bash -
      run_root "$PM" install -y nodejs
    fi
    run_root systemctl enable --now nginx
  else die "仅支持 Ubuntu/Debian、Alibaba Cloud Linux、CentOS/RHEL 系列"; fi
  java -version; node -v; npm -v; ok "服务器环境安装完成"
}

check_deps(){ need java; need npm; need curl; need git; command -v "$MODEL_CONVERT_BLENDER_COMMAND" >/dev/null 2>&1 || command -v "$MODEL_CONVERT_ASSIMP_COMMAND" >/dev/null 2>&1 || warn "未检测到 Blender/assimp；GLB可正常下载，OBJ/STL本地转换需安装转换器"; }

write_config(){
  [ -f "$ENV_FILE" ] || warn "未找到 $ENV_FILE，正在使用默认值；正式部署请先复制 deploy/env.example"
  mkdir -p "$LOG_DIR" "$RUN_DIR"
  existing_prop(){
    local key="$1" file="$BACKEND_DIR/application-local.properties"
    [ -f "$file" ] || return 0
    awk -F= -v k="$key" '$1==k { sub(/^[^=]*=/, ""); v=$0 } END { print v }' "$file"
  }
  # production 会重写 application-local.properties；如果 .env 未配置，尽量保留已手工写入的第三方密钥。
  [ -n "$REPLICATE_API_KEY" ] || REPLICATE_API_KEY="$(existing_prop "replicate.api.key" || true)"
  [ -n "$MODAO_API_KEY" ] || MODAO_API_KEY="$(existing_prop "modao.api.key" || true)"
  cat > "$BACKEND_DIR/application-local.properties" <<CFG
server.address=0.0.0.0
server.port=$APP_PORT
spring.datasource.url=jdbc:mysql://$DB_HOST:$DB_PORT/$DB_NAME?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
spring.datasource.username=$DB_USER
spring.datasource.password=$DB_PASSWORD
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
qwen.api.key=$QWEN_API_KEY
siliconflow.api.key=$SILICONFLOW_API_KEY
siliconflow.chat.model=$SILICONFLOW_CHAT_MODEL
siliconflow.image.model=$SILICONFLOW_IMAGE_MODEL
siliconflow.image.edit.model=$SILICONFLOW_IMAGE_EDIT_MODEL
tripo.api.key=$TRIPO_API_KEY
tripo.api.base-url=$TRIPO_API_BASE_URL
tripo.convert.base-url=$TRIPO_CONVERT_BASE_URL
tripo.model.version=$TRIPO_MODEL_VERSION
model.convert.prefer-local=$MODEL_CONVERT_PREFER_LOCAL
model.convert.blender-command=$MODEL_CONVERT_BLENDER_COMMAND
model.convert.assimp-command=$MODEL_CONVERT_ASSIMP_COMMAND
model.convert.timeout-seconds=$MODEL_CONVERT_TIMEOUT_SECONDS
replicate.api.key=$REPLICATE_API_KEY
replicate.api.base-url=$REPLICATE_API_BASE_URL
replicate.imagen.model=$REPLICATE_IMAGEN_MODEL
modao.api.key=$MODAO_API_KEY
modao.design.url=$MODAO_DESIGN_URL
modao.mcp.url=$MODAO_MCP_URL
modao.chrome.path=$MODAO_CHROME_PATH
kuaidi100.customer=$KUAIDI100_CUSTOMER
kuaidi100.key=$KUAIDI100_KEY
kuaidi100.callback-url=$KUAIDI100_CALLBACK_URL
kuaidi100.salt=$KUAIDI100_SALT
CFG
  chmod 600 "$BACKEND_DIR/application-local.properties" "$ENV_FILE" 2>/dev/null || true
  ok "生产配置已生成"
}

mysql_exec(){
  local sql="$1"
  if [ "$DB_HOST" = "127.0.0.1" ] || [ "$DB_HOST" = "localhost" ]; then
    if [ -z "$MYSQL_ADMIN_PASSWORD" ] && run_root mysql -u"$MYSQL_ADMIN_USER" -e "SELECT 1" >/dev/null 2>&1; then run_root mysql -u"$MYSQL_ADMIN_USER" -e "$sql"; return; fi
  fi
  MYSQL_PWD="$MYSQL_ADMIN_PASSWORD" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$MYSQL_ADMIN_USER" -e "$sql"
}

init_db(){
  need mysql
  [[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || die "DB_NAME只能包含字母、数字和下划线"
  [[ "$DB_USER" =~ ^[A-Za-z0-9_]+$ ]] || die "DB_USER格式不合法"
  local escaped=${DB_PASSWORD//\'/\'\'}
  mysql_exec "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$escaped'; ALTER USER '$DB_USER'@'%' IDENTIFIED BY '$escaped'; GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'%'; FLUSH PRIVILEGES;"
  ok "数据库及业务账号已就绪：$DB_NAME / $DB_USER"
}

build(){
  check_deps; write_config
  info "构建 Vue 前端"; cd "$FRONTEND_DIR"; [ -f package-lock.json ] && npm ci || npm install; npm run build
  rm -rf "$BACKEND_DIR/src/main/resources/static"/*; cp -R "$FRONTEND_DIR/dist/"* "$BACKEND_DIR/src/main/resources/static/"
  info "构建 Spring Boot"; cd "$BACKEND_DIR"; chmod +x mvnw; ./mvnw -DskipTests clean package
  find_jar; ok "应用构建完成：$JAR_FILE"
}
find_jar(){ JAR_FILE="$(find "$BACKEND_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' ! -name '*sources.jar' | head -1 || true)"; [ -n "$JAR_FILE" ] || die "未找到Jar，请先执行 build"; }
stop(){ if [ -f "$PID_FILE" ]; then pid=$(cat "$PID_FILE"); kill "$pid" 2>/dev/null || true; for _ in {1..20}; do ps -p "$pid" >/dev/null 2>&1 || break; sleep 1; done; rm -f "$PID_FILE"; fi; }
health(){ for _ in {1..45}; do curl -fsS "http://127.0.0.1:$APP_PORT/" >/dev/null 2>&1 && { ok "健康检查通过"; return; }; sleep 2; done; tail -100 "$APP_LOG" || true; die "健康检查失败"; }
start(){ mkdir -p "$RUN_DIR" "$LOG_DIR"; find_jar; if [ -f "$PID_FILE" ] && ps -p "$(cat "$PID_FILE")" >/dev/null 2>&1; then warn "服务已运行"; return; fi; cd "$BACKEND_DIR"; nohup java $JAVA_OPTS -jar "$JAR_FILE" >>"$APP_LOG" 2>&1 & echo $! > "$PID_FILE"; health; ok "服务已启动：http://服务器IP:$APP_PORT"; }
restart(){ stop; start; }
status(){ if [ -f "$PID_FILE" ] && ps -p "$(cat "$PID_FILE")" >/dev/null 2>&1; then ok "运行中 PID=$(cat "$PID_FILE") PORT=$APP_PORT"; else warn "未运行"; fi; curl -sS -o /dev/null -w 'HTTP %{http_code}\n' "http://127.0.0.1:$APP_PORT/" || true; }
logs(){ touch "$APP_LOG"; tail -f "$APP_LOG"; }

install_service(){
  find_jar; write_config
  local user="${SERVICE_USER:-$(id -un)}" service="/etc/systemd/system/$APP_NAME.service"
  local content="[Unit]
Description=Smart Pig Commercial Production Platform
After=network-online.target mysql.service mysqld.service
Wants=network-online.target

[Service]
Type=simple
User=$user
WorkingDirectory=$BACKEND_DIR
ExecStart=/usr/bin/java $JAVA_OPTS -jar $JAR_FILE
Restart=always
RestartSec=5
SuccessExitStatus=143
StandardOutput=append:$APP_LOG
StandardError=append:$APP_LOG
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target"
  if [ "$(id -u)" = 0 ]; then printf '%s\n' "$content" > "$service"; else printf '%s\n' "$content" | sudo tee "$service" >/dev/null; fi
  run_root systemctl daemon-reload; run_root systemctl enable "$APP_NAME"; stop || true; run_root systemctl restart "$APP_NAME"; sleep 3
  run_root systemctl --no-pager --full status "$APP_NAME" || { run_root journalctl -u "$APP_NAME" -n 100 --no-pager; exit 1; }
  ok "systemd开机自启已安装"
}

nginx(){
  local domain="${DOMAIN:-_}" conf="/etc/nginx/conf.d/$APP_NAME.conf"
  local content="server { listen 80; server_name $domain; client_max_body_size 30m; location / { proxy_pass http://127.0.0.1:$APP_PORT; proxy_http_version 1.1; proxy_set_header Host \$host; proxy_set_header X-Real-IP \$remote_addr; proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for; proxy_set_header X-Forwarded-Proto \$scheme; proxy_read_timeout 180s; } }"
  if [ "$(id -u)" = 0 ]; then printf '%s\n' "$content" > "$conf"; else printf '%s\n' "$content" | sudo tee "$conf" >/dev/null; fi
  run_root nginx -t; run_root systemctl enable --now nginx; run_root systemctl reload nginx; ok "Nginx已配置：http://$domain"
}

deploy(){ check_deps; write_config; init_db; build; restart; }
production(){ check_deps; write_config; init_db; build; install_service; nginx; }
case "${1:-deploy}" in
 install-deps) install_deps;; config) write_config;; init-db) init_db;; build) build;; start) start;; stop) stop;; restart) restart;; status) status;; logs) logs;; service) install_service;; nginx) nginx;; deploy) deploy;; production) production;; *) echo "用法: $0 {install-deps|config|init-db|build|deploy|production|start|stop|restart|status|logs|service|nginx}"; exit 1;; esac
