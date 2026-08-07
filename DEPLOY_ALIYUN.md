# 阿里云 ECS 一键部署

## 1. 推荐配置

- Ubuntu 22.04/24.04 或 Alibaba Cloud Linux 3
- 2 核 4 GB 起步，系统盘 40 GB+
- 安全组开放：`22`、`80`、`443`；仅调试时开放 `8080`
- 正式商用优先使用阿里云 RDS MySQL 8.0

## 2. 全新服务器：一键下载环境与代码

登录服务器：

```bash
ssh root@你的服务器公网IP
```

如果代码在 Git 仓库，执行下面一条命令（替换仓库地址）：

```bash
curl -fsSL https://你的仓库原始文件地址/deploy/bootstrap.sh | REPO_URL=https://你的Git仓库地址.git BRANCH=main APP_DIR=/opt/smart_pig bash
```

如果不能提供 bootstrap 原始文件地址，使用通用方式：

```bash
apt-get update && apt-get install -y git curl || yum install -y git curl
git clone https://你的Git仓库地址.git /opt/smart_pig
cd /opt/smart_pig
bash scripts/aliyun-start.sh install-deps
cp deploy/env.example .env
vim .env
bash scripts/aliyun-start.sh production
```

`install-deps` 自动安装 Java 17、Node.js 22、npm、Git、MySQL Server/客户端、Nginx、curl、lsof。

## 3. 配置 `.env`

```bash
cd /opt/smart_pig
cp deploy/env.example .env
vim .env
```

本机 MySQL 最少修改：

```dotenv
DB_NAME=shixun
DB_USER=smart_pig
DB_PASSWORD=一个高强度密码
MYSQL_ADMIN_USER=root
MYSQL_ADMIN_PASSWORD=
DOMAIN=你的域名或_
CORS_ALLOWED_ORIGINS=https://你的前端域名
AUTH_JWT_SECRET=至少32位随机字符串
SILICONFLOW_API_KEY=你的密钥
REPLICATE_API_KEY=你的r8开头Replicate密钥
```

首次没有管理员账号时，可临时补充：

```dotenv
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=你的管理员用户名
BOOTSTRAP_ADMIN_PASSWORD=至少12位高强度密码
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
BOOTSTRAP_ADMIN_PHONE=你的手机号
```

首次登录确认成功后，立刻将 `BOOTSTRAP_ADMIN_ENABLED` 改回 `false` 并重新执行部署。项目不会自动创建演示账号或默认密码。

使用阿里云 RDS 时：

```dotenv
INSTALL_LOCAL_MYSQL=false
DB_HOST=rm-xxxx.mysql.rds.aliyuncs.com
DB_PORT=3306
DB_NAME=shixun
DB_USER=你的RDS业务账号
DB_PASSWORD=你的RDS业务密码
MYSQL_ADMIN_USER=你的RDS管理员账号
MYSQL_ADMIN_PASSWORD=管理员密码
```

RDS 白名单需放行 ECS 内网 IP。不要把 `.env` 提交到 Git。

## 4. 一键生产部署和启动

```bash
cd /opt/smart_pig
bash scripts/aliyun-start.sh production
```

它会自动：

1. 加载 `.env`
2. 创建数据库和业务账号；空库仅首装一次基础 Schema
3. 安装前端依赖并构建 Vue
4. 将前端产物打入 Spring Boot
5. Maven 打包后端
6. 安装 `smart-pig.service` systemd 服务
7. 设置故障自动重启和开机自启
8. 配置 Nginx 反向代理到 `127.0.0.1:8080`

已有历史数据库时，首次升级到本版本前请先备份，并执行一次账号安全迁移：

```bash
mysql -u <数据库账号> -p shixun < shixun/src/main/resources/db/migration/V20260731_01__harden_account_bootstrap.sql
```

迁移不会删除旧演示账号；请在后台人工盘点、停用并重置其密码。

访问：`http://服务器公网IP/` 或 `http://你的域名/`。

## 5. 代码更新后一键发布

```bash
cd /opt/smart_pig
git pull
bash scripts/aliyun-start.sh production
```

如果只想用普通后台进程而不安装 systemd：

```bash
bash scripts/aliyun-start.sh deploy
```

## 6. 服务运维

```bash
# systemd生产服务
systemctl status smart-pig
systemctl restart smart-pig
systemctl stop smart-pig
journalctl -u smart-pig -f

# 应用文件日志
tail -f /opt/smart_pig/logs/smart-pig.log

# 脚本模式
bash scripts/aliyun-start.sh status
bash scripts/aliyun-start.sh logs
bash scripts/aliyun-start.sh restart
bash scripts/aliyun-start.sh stop
```

## 7. HTTPS

域名解析到 ECS 后，可安装 Certbot：

Ubuntu：

```bash
apt-get install -y certbot python3-certbot-nginx
certbot --nginx -d 你的域名
```

快递100回调配置为：

```dotenv
KUAIDI100_CALLBACK_URL=https://你的域名/api/logistics/callback/kuaidi100
```

## 8. 阿里云安全组

- `22`：建议只放行你的办公 IP
- `80`：`0.0.0.0/0`
- `443`：`0.0.0.0/0`
- `8080`：使用 Nginx 后不要对公网开放
- `3306`：本机 MySQL 不对公网开放；RDS 使用白名单

## 9. 故障排查

```bash
systemctl status smart-pig --no-pager -l
journalctl -u smart-pig -n 200 --no-pager
tail -200 /opt/smart_pig/logs/smart-pig.log
nginx -t
curl -I http://127.0.0.1:8080/
ss -lntp | grep -E ':80|:8080|:3306'
```

## 10. Tripo真实3D模型生成

在项目根目录 `.env` 配置：

```dotenv
TRIPO_API_KEY=你的Tripo API Key
TRIPO_API_BASE_URL=https://openapi.tripo3d.com/v3
TRIPO_MODEL_VERSION=v3.1-20260211

# 下载 OBJ/STL 时优先服务器本地转换 GLB -> OBJ/STL
MODEL_CONVERT_PREFER_LOCAL=true
MODEL_CONVERT_FALLBACK_TRIPO=false
MODEL_CONVERT_BLENDER_COMMAND=blender
MODEL_CONVERT_ASSIMP_COMMAND=assimp
MODEL_CONVERT_NODE_COMMAND=node
MODEL_CONVERT_TIMEOUT_SECONDS=300
```

然后重新部署：

```bash
cd /opt/smart_pig
bash scripts/aliyun-start.sh production
```

在“创意设计 → 3D辅助建模”上传产品参考图，系统会提交Tripo图生3D任务，自动轮询进度，并将完成的GLB模型和预览图下载到本系统资产目录，避免Tripo临时下载地址过期。

模型预览仍使用 GLB；下载 OBJ/STL 时后端会优先调用服务器本地 Blender（推荐）或 assimp 转换，并把转换结果登记到资产库缓存。若服务器没有 Blender/assimp，系统会使用项目内置 Node + three 做基础几何转换兜底；如果仍提示“服务器未安装模型转换器”，请先执行：

```bash
cd /opt/smart_pig
bash scripts/aliyun-start.sh install-deps
```

如果系统源无法安装 Blender，也可以手动安装后在 `.env` 中把 `MODEL_CONVERT_BLENDER_COMMAND` 改成实际命令路径。

## 11. Google Imagen 4 / 墨刀 2D 生图配置

一键部署脚本会根据项目根目录 `.env` 生成 `shixun/application-local.properties`。因此生产环境不要只手工改 `application-local.properties`，否则下次执行 `bash scripts/aliyun-start.sh production` 可能被覆盖；请优先写入 `.env`：

```dotenv
REPLICATE_API_KEY=你的r8开头Replicate密钥
REPLICATE_API_BASE_URL=https://api.replicate.com/v1
REPLICATE_IMAGEN_MODEL=google/imagen-4

MODAO_API_KEY=你的墨刀令牌
MODAO_DESIGN_URL=https://modao.cc/ai/design/spmrsxjgcyi6g0h1/6a5dd48151e5a21110c1697a
MODAO_MCP_URL=https://modao.cc/agent-py/ai/mcp
```

重新部署后可验证 Imagen 4 是否生效：

```bash
curl -s http://127.0.0.1:8080/api/creative/ai/imagen/config
```

返回 `"configured":true` 表示 2D 生图的 Google Imagen 4 按钮可用。

## 12. 微信小程序 JSAPI 支付上线

小程序支付只在服务端创建订单，客户端通过 `wx.requestPayment` 展示支付界面；到账、退款和异常订单均以微信支付 API v3 的验签通知或主动查询为准。不要在小程序代码中放 AppSecret、商户私钥或 APIv3 Key。

上线前确认：

1. 微信商户平台已开通 JSAPI/小程序支付，且商户号已绑定小程序 AppID `wxd1ba9e6e01d0e3db`；小程序后台已配置服务器业务域名和 request 合法域名。
2. 服务器已启用 HTTPS，并将以下两个地址登记到微信商户平台，且反向代理不会删除 `Wechatpay-*` 请求头：

   - `https://你的域名/api/payments/wechat/notify`
   - `https://你的域名/api/payments/wechat/refund-notify`

3. 将商户私钥、微信支付公钥放在服务器受限目录（例如 `/opt/smart_pig/secrets`），权限设为仅运行用户可读；微信支付公钥 ID 必须与 `.env` 中的值一致。
4. 在 `.env` 填写并检查以下配置（真实密钥不要提交 Git）：

   ```dotenv
   PAYMENT_WECHAT_ENABLED=true
   PAYMENT_WECHAT_APP_ID=wxd1ba9e6e01d0e3db
   PAYMENT_WECHAT_MINI_APP_SECRET=小程序AppSecret
   PAYMENT_WECHAT_MCH_ID=微信商户号
   PAYMENT_WECHAT_SERIAL_NO=商户证书序列号
   PAYMENT_WECHAT_PRIVATE_KEY_PATH=/opt/smart_pig/secrets/apiclient_key.pem
   PAYMENT_WECHAT_API_V3_KEY=32位APIv3Key
   PAYMENT_WECHAT_NOTIFY_URL=https://你的域名/api/payments/wechat/notify
   PAYMENT_WECHAT_REFUND_NOTIFY_URL=https://你的域名/api/payments/wechat/refund-notify
   PAYMENT_WECHAT_PLATFORM_PUBLIC_KEY_PATH=/opt/smart_pig/secrets/wechatpay_public_key.pem
   PAYMENT_WECHAT_PLATFORM_SERIAL_NO=微信支付公钥ID
   PAYMENT_WECHAT_RECONCILE_ENABLED=true
   ```

   网页端“微信扫码登录”还需要在微信开放平台创建网站应用，并额外配置：

   ```dotenv
   PAYMENT_WECHAT_WEB_APP_ID=微信开放平台网站应用AppID
   PAYMENT_WECHAT_WEB_APP_SECRET=微信开放平台网站应用AppSecret
   PAYMENT_WECHAT_WEB_REDIRECT_URI=https://你的域名/api/users/wechat-web/callback
   PAYMENT_WECHAT_WEB_SUCCESS_URL=https://你的域名/
   ```

   开放平台网站应用的授权回调域名填写 `你的域名`，不能把小程序 AppID/AppSecret 直接当作网站应用凭据使用。

   不配置以上 `PAYMENT_WECHAT_WEB_*` 也不影响网页端的默认微信登录：用户可扫描网页生成的“之间智造”小程序码并在小程序确认。该方案只复用已有的小程序 AppID/AppSecret，但必须重新构建、上传并发布小程序，确保包含 `pages/web-login/index` 页面。

5. 历史数据库先备份，再执行支付扩展表迁移，然后重新部署：

   ```bash
   mysql -u <数据库账号> -p shixun < shixun/src/main/resources/db/migration/V20260803_01__wechat_jsapi_payment.sql
   bash scripts/aliyun-start.sh production
   ```

订单接口会先绑定 `uni.login` 的临时 code，再返回 `paymentParams`；前端支付成功回调不会直接增加积分。退款仅允许已核验的微信订单，且充值额度尚未被创作消费；人工收款码订单需线下退款。网络超时、支付回调丢失、退款 `PROCESSING/ABNORMAL` 会保留为待核对状态，不应让用户重复付款。

每日账单任务默认北京时间 10:30 下载昨日交易/退款/资金流水账单，14:30 自动重试失败项。系统会验签请求、校验账单摘要并记录差异，不会根据 CSV 自动改余额；管理员应在“支付异常/日账单”接口完成复核。
