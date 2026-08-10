#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="smart-pig"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
if [ -f "$ENV_FILE" ]; then set -a; source "$ENV_FILE"; set +a; fi

APP_PORT="${APP_PORT:-8080}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1536m -XX:+UseG1GC -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai}"
AUTH_JWT_SECRET="${AUTH_JWT_SECRET:-}"; AUTH_JWT_EXPIRES_SECONDS="${AUTH_JWT_EXPIRES_SECONDS:-28800}"
BACKEND_DIR="$ROOT_DIR/shixun"; FRONTEND_DIR="$ROOT_DIR/shixun-vue"
RUN_DIR="$ROOT_DIR/runtime"; LOG_DIR="$ROOT_DIR/logs"; PID_FILE="$RUN_DIR/$APP_NAME.pid"; APP_LOG="$LOG_DIR/$APP_NAME.log"
DB_HOST="${DB_HOST:-127.0.0.1}"; DB_PORT="${DB_PORT:-3306}"; DB_NAME="${DB_NAME:-shixun}"; DB_USER="${DB_USER:-smart_pig}"
DB_PASSWORD="${DB_PASSWORD:-}"; MYSQL_ADMIN_USER="${MYSQL_ADMIN_USER:-root}"; MYSQL_ADMIN_PASSWORD="${MYSQL_ADMIN_PASSWORD:-}"
CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-}"
PAYMENT_MANUAL_QR_URL="${PAYMENT_MANUAL_QR_URL:-/payment-collection-qr.jpg}"
PAYMENT_MANUAL_QR_ENABLED="${PAYMENT_MANUAL_QR_ENABLED:-false}"
CREATIVE_ASSET_PRIVATE_ROOT="${CREATIVE_ASSET_PRIVATE_ROOT:-$BACKEND_DIR/data/creative-assets}"
BOOTSTRAP_ADMIN_ENABLED="${BOOTSTRAP_ADMIN_ENABLED:-false}"; BOOTSTRAP_ADMIN_USERNAME="${BOOTSTRAP_ADMIN_USERNAME:-}"; BOOTSTRAP_ADMIN_PASSWORD="${BOOTSTRAP_ADMIN_PASSWORD:-}"; BOOTSTRAP_ADMIN_EMAIL="${BOOTSTRAP_ADMIN_EMAIL:-}"; BOOTSTRAP_ADMIN_PHONE="${BOOTSTRAP_ADMIN_PHONE:-}"; BOOTSTRAP_ADMIN_AGE="${BOOTSTRAP_ADMIN_AGE:-30}"
SILICONFLOW_API_KEY="${SILICONFLOW_API_KEY:-}"; SILICONFLOW_CHAT_MODEL="${SILICONFLOW_CHAT_MODEL:-Qwen/Qwen3-32B}"
SILICONFLOW_IMAGE_MODEL="${SILICONFLOW_IMAGE_MODEL:-Kwai-Kolors/Kolors}"; SILICONFLOW_IMAGE_EDIT_MODEL="${SILICONFLOW_IMAGE_EDIT_MODEL:-Qwen/Qwen-Image-Edit-2509}"
QWEN_API_KEY="${QWEN_API_KEY:-}"; TRIPO_API_KEY="${TRIPO_API_KEY:-}"; TRIPO_API_BASE_URL="${TRIPO_API_BASE_URL:-https://openapi.tripo3d.com/v3}"; TRIPO_CONVERT_BASE_URL="${TRIPO_CONVERT_BASE_URL:-https://api.tripo3d.ai/v2/openapi}"; TRIPO_MODEL_VERSION="${TRIPO_MODEL_VERSION:-v3.1-20260211}"
MODEL_CONVERT_PREFER_LOCAL="${MODEL_CONVERT_PREFER_LOCAL:-true}"; MODEL_CONVERT_FALLBACK_TRIPO="${MODEL_CONVERT_FALLBACK_TRIPO:-false}"; MODEL_CONVERT_BLENDER_COMMAND="${MODEL_CONVERT_BLENDER_COMMAND:-blender}"; MODEL_CONVERT_ASSIMP_COMMAND="${MODEL_CONVERT_ASSIMP_COMMAND:-assimp}"; MODEL_CONVERT_NODE_COMMAND="${MODEL_CONVERT_NODE_COMMAND:-node}"; MODEL_CONVERT_TIMEOUT_SECONDS="${MODEL_CONVERT_TIMEOUT_SECONDS:-300}"
REPLICATE_API_KEY="${REPLICATE_API_KEY:-}"; REPLICATE_API_BASE_URL="${REPLICATE_API_BASE_URL:-https://api.replicate.com/v1}"; REPLICATE_IMAGEN_MODEL="${REPLICATE_IMAGEN_MODEL:-google/imagen-4}"
JIMENG_API_KEY="${JIMENG_API_KEY:-}"; JIMENG_ACCESS_KEY_ID="${JIMENG_ACCESS_KEY_ID:-}"; JIMENG_SECRET_ACCESS_KEY="${JIMENG_SECRET_ACCESS_KEY:-}"; JIMENG_REGION="${JIMENG_REGION:-cn-north-1}"; JIMENG_SERVICE="${JIMENG_SERVICE:-cv}"; JIMENG_API_BASE_URL="${JIMENG_API_BASE_URL:-https://visual.volcengineapi.com}"; JIMENG_REQ_KEY="${JIMENG_REQ_KEY:-jimeng_seedream46_cvtob}"; JIMENG_POLL_MAX_SECONDS="${JIMENG_POLL_MAX_SECONDS:-180}"
MODAO_API_KEY="${MODAO_API_KEY:-}"; MODAO_DESIGN_URL="${MODAO_DESIGN_URL:-https://modao.cc/ai/design/spmrsxjgcyi6g0h1/6a5dd48151e5a21110c1697a}"; MODAO_MCP_URL="${MODAO_MCP_URL:-https://modao.cc/agent-py/ai/mcp}"; MODAO_CHROME_PATH="${MODAO_CHROME_PATH:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
KUAIDI100_CUSTOMER="${KUAIDI100_CUSTOMER:-}"; KUAIDI100_KEY="${KUAIDI100_KEY:-}"; KUAIDI100_CALLBACK_URL="${KUAIDI100_CALLBACK_URL:-}"; KUAIDI100_SALT="${KUAIDI100_SALT:-}"
# The PAYMENT_WECHAT_* names are canonical. The fallbacks retain compatibility
# with earlier setup instructions without copying secrets into source control.
PAYMENT_WECHAT_ENABLED="${PAYMENT_WECHAT_ENABLED:-${WECHAT_PAY_ENABLED:-false}}"
PAYMENT_WECHAT_APP_ID="${PAYMENT_WECHAT_APP_ID:-${WECHAT_PAY_APP_ID:-${WECHAT_APP_ID:-}}}"
PAYMENT_WECHAT_MINI_APP_SECRET="${PAYMENT_WECHAT_MINI_APP_SECRET:-${WECHAT_PAY_MINI_APP_SECRET:-${WECHAT_MINI_APP_SECRET:-}}}"
PAYMENT_WECHAT_WEB_APP_ID="${PAYMENT_WECHAT_WEB_APP_ID:-}"
PAYMENT_WECHAT_WEB_APP_SECRET="${PAYMENT_WECHAT_WEB_APP_SECRET:-}"
PAYMENT_WECHAT_WEB_REDIRECT_URI="${PAYMENT_WECHAT_WEB_REDIRECT_URI:-https://zhijiansk.com/api/users/wechat-web/callback}"
PAYMENT_WECHAT_WEB_SUCCESS_URL="${PAYMENT_WECHAT_WEB_SUCCESS_URL:-https://zhijiansk.com/}"
PAYMENT_WECHAT_MCH_ID="${PAYMENT_WECHAT_MCH_ID:-${WECHAT_PAY_MCH_ID:-}}"
PAYMENT_WECHAT_SERIAL_NO="${PAYMENT_WECHAT_SERIAL_NO:-${WECHAT_PAY_CERT_SERIAL_NO:-${WECHAT_PAY_SERIAL_NO:-}}}"
PAYMENT_WECHAT_PRIVATE_KEY_PATH="${PAYMENT_WECHAT_PRIVATE_KEY_PATH:-${WECHAT_PAY_PRIVATE_KEY_PATH:-}}"
PAYMENT_WECHAT_API_V3_KEY="${PAYMENT_WECHAT_API_V3_KEY:-${WECHAT_PAY_API_V3_KEY:-}}"
PAYMENT_WECHAT_NOTIFY_URL="${PAYMENT_WECHAT_NOTIFY_URL:-${WECHAT_PAY_NOTIFY_URL:-}}"
PAYMENT_WECHAT_REFUND_NOTIFY_URL="${PAYMENT_WECHAT_REFUND_NOTIFY_URL:-${WECHAT_PAY_REFUND_NOTIFY_URL:-}}"
PAYMENT_WECHAT_PLATFORM_PUBLIC_KEY_PATH="${PAYMENT_WECHAT_PLATFORM_PUBLIC_KEY_PATH:-${WECHAT_PAY_PUBLIC_KEY_PATH:-}}"
PAYMENT_WECHAT_PLATFORM_SERIAL_NO="${PAYMENT_WECHAT_PLATFORM_SERIAL_NO:-${WECHAT_PAY_PUBLIC_KEY_ID:-${WECHAT_PAY_PLATFORM_SERIAL_NO:-}}}"
PAYMENT_WECHAT_CALLBACK_MAX_AGE_SECONDS="${PAYMENT_WECHAT_CALLBACK_MAX_AGE_SECONDS:-300}"; PAYMENT_WECHAT_RECONCILE_ENABLED="${PAYMENT_WECHAT_RECONCILE_ENABLED:-true}"; PAYMENT_WECHAT_RECONCILE_DELAY_MS="${PAYMENT_WECHAT_RECONCILE_DELAY_MS:-300000}"; PAYMENT_WECHAT_RECONCILE_LIMIT="${PAYMENT_WECHAT_RECONCILE_LIMIT:-40}"; PAYMENT_WECHAT_DAILY_RECONCILE_CRON="${PAYMENT_WECHAT_DAILY_RECONCILE_CRON:-0 30 10 * * *}"; PAYMENT_WECHAT_DAILY_RECONCILE_RETRY_CRON="${PAYMENT_WECHAT_DAILY_RECONCILE_RETRY_CRON:-0 30 14 * * *}"
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

check_deps(){ need java; need npm; need curl; need git; command -v "$MODEL_CONVERT_BLENDER_COMMAND" >/dev/null 2>&1 || command -v "$MODEL_CONVERT_ASSIMP_COMMAND" >/dev/null 2>&1 || command -v "$MODEL_CONVERT_NODE_COMMAND" >/dev/null 2>&1 || warn "未检测到 Blender/assimp/Node；GLB可正常下载，OBJ/STL本地转换需安装转换器"; }

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
  [ -n "$JIMENG_API_KEY" ] || JIMENG_API_KEY="$(existing_prop "jimeng.api.key" || true)"
  [ -n "$JIMENG_ACCESS_KEY_ID" ] || JIMENG_ACCESS_KEY_ID="$(existing_prop "jimeng.access-key-id" || true)"
  [ -n "$JIMENG_SECRET_ACCESS_KEY" ] || JIMENG_SECRET_ACCESS_KEY="$(existing_prop "jimeng.secret-access-key" || true)"
  [ -n "$MODAO_API_KEY" ] || MODAO_API_KEY="$(existing_prop "modao.api.key" || true)"
  cat > "$BACKEND_DIR/application-local.properties" <<CFG
server.address=0.0.0.0
server.port=$APP_PORT
auth.jwt.secret=$AUTH_JWT_SECRET
auth.jwt.expires-seconds=$AUTH_JWT_EXPIRES_SECONDS
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:-http://localhost:5176,http://127.0.0.1:5176,http://localhost:5173,http://127.0.0.1:5173}
payment.manual-qr-url=$PAYMENT_MANUAL_QR_URL
payment.manual-qr-enabled=$PAYMENT_MANUAL_QR_ENABLED
creative.asset.private-root=$CREATIVE_ASSET_PRIVATE_ROOT
app.bootstrap.admin.enabled=$BOOTSTRAP_ADMIN_ENABLED
app.bootstrap.admin.username=$BOOTSTRAP_ADMIN_USERNAME
app.bootstrap.admin.password=$BOOTSTRAP_ADMIN_PASSWORD
app.bootstrap.admin.email=$BOOTSTRAP_ADMIN_EMAIL
app.bootstrap.admin.phone=$BOOTSTRAP_ADMIN_PHONE
app.bootstrap.admin.age=$BOOTSTRAP_ADMIN_AGE
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
model.convert.fallback-tripo=$MODEL_CONVERT_FALLBACK_TRIPO
model.convert.blender-command=$MODEL_CONVERT_BLENDER_COMMAND
model.convert.assimp-command=$MODEL_CONVERT_ASSIMP_COMMAND
model.convert.node-command=$MODEL_CONVERT_NODE_COMMAND
model.convert.timeout-seconds=$MODEL_CONVERT_TIMEOUT_SECONDS
replicate.api.key=$REPLICATE_API_KEY
replicate.api.base-url=$REPLICATE_API_BASE_URL
replicate.imagen.model=$REPLICATE_IMAGEN_MODEL
jimeng.api.key=$JIMENG_API_KEY
jimeng.access-key-id=$JIMENG_ACCESS_KEY_ID
jimeng.secret-access-key=$JIMENG_SECRET_ACCESS_KEY
jimeng.region=$JIMENG_REGION
jimeng.service=$JIMENG_SERVICE
jimeng.api.base-url=$JIMENG_API_BASE_URL
jimeng.req-key=$JIMENG_REQ_KEY
jimeng.poll.max-seconds=$JIMENG_POLL_MAX_SECONDS
modao.api.key=$MODAO_API_KEY
modao.design.url=$MODAO_DESIGN_URL
modao.mcp.url=$MODAO_MCP_URL
modao.chrome.path=$MODAO_CHROME_PATH
kuaidi100.customer=$KUAIDI100_CUSTOMER
kuaidi100.key=$KUAIDI100_KEY
kuaidi100.callback-url=$KUAIDI100_CALLBACK_URL
kuaidi100.salt=$KUAIDI100_SALT
payment.wechat.enabled=$PAYMENT_WECHAT_ENABLED
payment.wechat.app-id=$PAYMENT_WECHAT_APP_ID
payment.wechat.mini-app-secret=$PAYMENT_WECHAT_MINI_APP_SECRET
payment.wechat.web-app-id=$PAYMENT_WECHAT_WEB_APP_ID
payment.wechat.web-app-secret=$PAYMENT_WECHAT_WEB_APP_SECRET
payment.wechat.web-redirect-uri=$PAYMENT_WECHAT_WEB_REDIRECT_URI
payment.wechat.web-success-url=$PAYMENT_WECHAT_WEB_SUCCESS_URL
payment.wechat.mch-id=$PAYMENT_WECHAT_MCH_ID
payment.wechat.serial-no=$PAYMENT_WECHAT_SERIAL_NO
payment.wechat.private-key-path=$PAYMENT_WECHAT_PRIVATE_KEY_PATH
payment.wechat.api-v3-key=$PAYMENT_WECHAT_API_V3_KEY
payment.wechat.notify-url=$PAYMENT_WECHAT_NOTIFY_URL
payment.wechat.refund-notify-url=$PAYMENT_WECHAT_REFUND_NOTIFY_URL
payment.wechat.platform-public-key-path=$PAYMENT_WECHAT_PLATFORM_PUBLIC_KEY_PATH
payment.wechat.platform-serial-no=$PAYMENT_WECHAT_PLATFORM_SERIAL_NO
payment.wechat.callback-max-age-seconds=$PAYMENT_WECHAT_CALLBACK_MAX_AGE_SECONDS
payment.wechat.reconcile-enabled=$PAYMENT_WECHAT_RECONCILE_ENABLED
payment.wechat.reconcile-delay-ms=$PAYMENT_WECHAT_RECONCILE_DELAY_MS
payment.wechat.reconcile-limit=$PAYMENT_WECHAT_RECONCILE_LIMIT
payment.wechat.daily-reconcile-cron=$PAYMENT_WECHAT_DAILY_RECONCILE_CRON
payment.wechat.daily-reconcile-retry-cron=$PAYMENT_WECHAT_DAILY_RECONCILE_RETRY_CRON
spring.mail.host=$EMAIL_SMTP_HOST
spring.mail.port=${EMAIL_SMTP_PORT:-465}
spring.mail.username=$EMAIL_SMTP_USERNAME
spring.mail.password=$EMAIL_SMTP_PASSWORD
spring.mail.protocol=${EMAIL_SMTP_PROTOCOL:-smtps}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.ssl.enable=${EMAIL_SMTP_SSL:-true}
spring.mail.properties.mail.smtp.starttls.enable=${EMAIL_SMTP_STARTTLS:-false}
app.email.verification-enabled=${EMAIL_VERIFICATION_ENABLED:-false}
app.email.from=${EMAIL_FROM:-$EMAIL_SMTP_USERNAME}
app.email.verification-secret=$EMAIL_VERIFICATION_SECRET
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

mysql_query(){
  local sql="$1"
  if [ "$DB_HOST" = "127.0.0.1" ] || [ "$DB_HOST" = "localhost" ]; then
    if [ -z "$MYSQL_ADMIN_PASSWORD" ] && run_root mysql -u"$MYSQL_ADMIN_USER" -e "SELECT 1" >/dev/null 2>&1; then
      run_root mysql -N -B -u"$MYSQL_ADMIN_USER" -e "$sql"
      return
    fi
  fi
  MYSQL_PWD="$MYSQL_ADMIN_PASSWORD" mysql -N -B -h"$DB_HOST" -P"$DB_PORT" -u"$MYSQL_ADMIN_USER" -e "$sql"
}

mysql_import_file(){
  local file="$1"
  [ -f "$file" ] || die "找不到数据库脚本：$file"
  info "导入数据库脚本：$(basename "$file")"
  # 历史 schema 文件包含面向存量库的重复 ALTER；--force 允许这些已存在
  # 的列/索引报错后继续执行，随后用关键表检查确认真正的建表语句已完成。
  # 两份历史脚本包含固定的 `USE shixun`；删除这两类数据库级指令，避免
  # DB_NAME 使用自定义名称时误把表导入另一个数据库。
  if [ "$DB_HOST" = "127.0.0.1" ] || [ "$DB_HOST" = "localhost" ]; then
    if [ -z "$MYSQL_ADMIN_PASSWORD" ] && run_root mysql -u"$MYSQL_ADMIN_USER" -e "SELECT 1" >/dev/null 2>&1; then
      if ! sed -E '/^[[:space:]]*CREATE DATABASE IF NOT EXISTS[[:space:]]+shixun([[:space:]]|;)/d; /^[[:space:]]*USE[[:space:]]+shixun[[:space:]]*;/d' "$file" \
        | run_root mysql --force --binary-mode -u"$MYSQL_ADMIN_USER" "$DB_NAME"; then
        warn "$(basename "$file") 包含可忽略的历史兼容错误，继续执行关键表检查"
      fi
      return
    fi
  fi
  if ! sed -E '/^[[:space:]]*CREATE DATABASE IF NOT EXISTS[[:space:]]+shixun([[:space:]]|;)/d; /^[[:space:]]*USE[[:space:]]+shixun[[:space:]]*;/d' "$file" \
    | MYSQL_PWD="$MYSQL_ADMIN_PASSWORD" mysql --force --binary-mode -h"$DB_HOST" -P"$DB_PORT" -u"$MYSQL_ADMIN_USER" "$DB_NAME"; then
    warn "$(basename "$file") 包含可忽略的历史兼容错误，继续执行关键表检查"
  fi
}

init_db(){
  need mysql
  [ -n "$DB_PASSWORD" ] || die "DB_PASSWORD 不能为空，请在 .env 中设置高强度数据库密码"
  [[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || die "DB_NAME只能包含字母、数字和下划线"
  [[ "$DB_USER" =~ ^[A-Za-z0-9_]+$ ]] || die "DB_USER格式不合法"
  local escaped=${DB_PASSWORD//\'/\'\'}
  mysql_exec "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$escaped'; ALTER USER '$DB_USER'@'%' IDENTIFIED BY '$escaped'; GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'%'; FLUSH PRIVILEGES;"
  init_schema_if_needed
  # This migration is idempotent and must also run for existing deployments;
  # the application uses the extension tables on its first payment request.
  mysql_import_file "$BACKEND_DIR/src/main/resources/db/migration/V20260803_01__wechat_jsapi_payment.sql"
  # Historical project sales are analytics facts only; they never mutate orders
  # or inventory and are safe to re-import by source batch/row.
  mysql_import_file "$BACKEND_DIR/src/main/resources/db/migration/V20260804_01__historical_sales_insights.sql"
  mysql_import_file "$BACKEND_DIR/src/main/resources/db/migration/V20260804_01__historical_sales_data.sql"
  mysql_import_file "$BACKEND_DIR/src/main/resources/db/migration/V20260805_01__consumer_sample_payment.sql"
  mysql_import_file "$BACKEND_DIR/src/main/resources/db/migration/V20260807_01__consumer_account_security.sql"
  mysql_import_file "$BACKEND_DIR/src/main/resources/db/migration/V20260807_02__email_registration_verification.sql"
  mysql_import_file "$BACKEND_DIR/src/main/resources/db/migration/V20260808_01__selection_knowledge_base.sql"
  mysql_import_file "$BACKEND_DIR/src/main/resources/db/migration/V20260810_01__commercial_productization_mvp.sql"
  mysql_import_file "$BACKEND_DIR/src/main/resources/db/migration/V20260810_02__commercial_quote_sample_payment.sql"
  ok "数据库及业务账号已就绪：$DB_NAME / $DB_USER"
}

init_schema_if_needed(){
  local table_count
  table_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='user'")"
  if [ "${table_count:-0}" != "0" ]; then
    info "检测到已有业务表，跳过首装 Schema 导入"
    return
  fi
  warn "检测到空数据库，正在执行一次性基础 Schema 导入；已有数据库不会自动覆盖"
  mysql_import_file "$BACKEND_DIR/src/main/resources/schema.sql"
  mysql_import_file "$BACKEND_DIR/src/main/resources/and_taste_schema.sql"
  local required
  required="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name IN ('user','digital_asset','ai_generation_job','workflow_application','warehouse_inventory')")"
  [ "${required:-0}" = "5" ] || die "基础 Schema 导入不完整，请查看上方导入日志后修复数据库"
  ok "基础 Schema 首装完成"
}

build(){
  check_deps; write_config
  info "构建 Vue 前端"; cd "$FRONTEND_DIR"; [ -f package-lock.json ] && npm ci || npm install; npm run build
  # The manual payment flow depends on the QR image being packaged with the
  # same-origin Spring Boot static resources.  Fail before deleting the
  # existing bundle if the frontend build accidentally omitted it.
  [ -f "$FRONTEND_DIR/dist/payment-collection-qr.jpg" ] || die "前端构建产物缺少 payment-collection-qr.jpg；请确认 shixun-vue/public/payment-collection-qr.jpg 存在"
  # Static/generated and static/uploads are runtime data, not build output.
  # Replace only the web bundle so a frontend release cannot delete users'
  # previously generated images/models or uploaded reference files.
  find "$BACKEND_DIR/src/main/resources/static" -mindepth 1 -maxdepth 1 \
    ! -name generated ! -name uploads -exec rm -rf -- {} +
  # Copy the directory itself (including dotfiles) instead of relying on a
  # shell glob; this keeps model-preview and any future static assets intact.
  cp -a "$FRONTEND_DIR/dist/." "$BACKEND_DIR/src/main/resources/static/"
  [ -f "$BACKEND_DIR/src/main/resources/static/payment-collection-qr.jpg" ] || die "静态资源同步失败：payment-collection-qr.jpg 未复制到 Spring Boot 目录"
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
  # The material-variant upload limit is 100MB and local 3D conversion may
  # run for up to five minutes.  Keep the reverse proxy above those backend
  # limits, otherwise a valid C-end upload/conversion fails at Nginx first.
  local content="server { listen 80; server_name $domain; client_max_body_size 120m; location / { proxy_pass http://127.0.0.1:$APP_PORT; proxy_http_version 1.1; proxy_set_header Host \$host; proxy_set_header X-Real-IP \$remote_addr; proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for; proxy_set_header X-Forwarded-Proto \$scheme; proxy_read_timeout 360s; } }"
  if [ "$(id -u)" = 0 ]; then printf '%s\n' "$content" > "$conf"; else printf '%s\n' "$content" | sudo tee "$conf" >/dev/null; fi
  run_root nginx -t; run_root systemctl enable --now nginx; run_root systemctl reload nginx; ok "Nginx已配置：http://$domain"
}

validate_runtime_config(){
  [ -n "$AUTH_JWT_SECRET" ] || die "AUTH_JWT_SECRET 不能为空，请在 .env 中设置随机密钥"
  [ "${#AUTH_JWT_SECRET}" -ge 32 ] || die "AUTH_JWT_SECRET 至少需要32个字符"
  [[ "$AUTH_JWT_SECRET" != *change-this* && "$AUTH_JWT_SECRET" != *development-jwt-secret* ]] || die "AUTH_JWT_SECRET 不能使用示例密钥"
  [ -n "$CORS_ALLOWED_ORIGINS" ] || die "CORS_ALLOWED_ORIGINS 不能为空，请填写实际前端 HTTPS 域名"
  [[ "$CORS_ALLOWED_ORIGINS" != *\** && "$CORS_ALLOWED_ORIGINS" != *null* ]] || die "CORS_ALLOWED_ORIGINS 不允许使用 * 或 null"
  if [ "$BOOTSTRAP_ADMIN_ENABLED" = "true" ]; then
    [ -n "$BOOTSTRAP_ADMIN_USERNAME" ] && [ -n "$BOOTSTRAP_ADMIN_PASSWORD" ] && [ -n "$BOOTSTRAP_ADMIN_EMAIL" ] && [ -n "$BOOTSTRAP_ADMIN_PHONE" ] || die "启用 BOOTSTRAP_ADMIN_ENABLED 时必须完整填写 BOOTSTRAP_ADMIN_*"
    [ "${#BOOTSTRAP_ADMIN_PASSWORD}" -ge 12 ] || die "BOOTSTRAP_ADMIN_PASSWORD 至少需要12个字符"
  fi
  if [ "$PAYMENT_WECHAT_ENABLED" = "true" ]; then
    [ "$PAYMENT_WECHAT_APP_ID" = "wxd1ba9e6e01d0e3db" ] || die "PAYMENT_WECHAT_APP_ID 必须与已发布小程序 AppID wxd1ba9e6e01d0e3db 一致"
    [ -n "$PAYMENT_WECHAT_MINI_APP_SECRET" ] && [ -n "$PAYMENT_WECHAT_MCH_ID" ] && [ -n "$PAYMENT_WECHAT_SERIAL_NO" ] || die "启用微信支付时必须完整填写商户和小程序配置"
    [ -n "$PAYMENT_WECHAT_API_V3_KEY" ] && [ "${#PAYMENT_WECHAT_API_V3_KEY}" -eq 32 ] || die "PAYMENT_WECHAT_API_V3_KEY 必须为32字节"
    [ -n "$PAYMENT_WECHAT_NOTIFY_URL" ] && [[ "$PAYMENT_WECHAT_NOTIFY_URL" == https://*/api/payments/wechat/notify ]] || die "PAYMENT_WECHAT_NOTIFY_URL 必须是公网 HTTPS 支付回调地址"
    [ -n "$PAYMENT_WECHAT_REFUND_NOTIFY_URL" ] && [[ "$PAYMENT_WECHAT_REFUND_NOTIFY_URL" == https://*/api/payments/wechat/refund-notify ]] || die "PAYMENT_WECHAT_REFUND_NOTIFY_URL 必须是公网 HTTPS 退款回调地址"
    [ -r "$PAYMENT_WECHAT_PRIVATE_KEY_PATH" ] || die "找不到或不可读 PAYMENT_WECHAT_PRIVATE_KEY_PATH"
    [ -r "$PAYMENT_WECHAT_PLATFORM_PUBLIC_KEY_PATH" ] || die "找不到或不可读 PAYMENT_WECHAT_PLATFORM_PUBLIC_KEY_PATH"
    [ -n "$PAYMENT_WECHAT_PLATFORM_SERIAL_NO" ] || die "PAYMENT_WECHAT_PLATFORM_SERIAL_NO 不能为空"
  fi
}
deploy(){ check_deps; validate_runtime_config; write_config; init_db; build; restart; }
production(){ check_deps; validate_runtime_config; write_config; init_db; build; install_service; nginx; }
case "${1:-deploy}" in
 install-deps) install_deps;; config) write_config;; init-db) init_db;; build) build;; start) start;; stop) stop;; restart) restart;; status) status;; logs) logs;; service) install_service;; nginx) nginx;; deploy) deploy;; production) production;; *) echo "用法: $0 {install-deps|config|init-db|build|deploy|production|start|stop|restart|status|logs|service|nginx}"; exit 1;; esac
