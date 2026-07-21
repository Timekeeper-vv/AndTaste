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
SILICONFLOW_API_KEY=你的密钥
REPLICATE_API_KEY=你的r8开头Replicate密钥
```

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
2. 创建数据库和业务账号
3. 安装前端依赖并构建 Vue
4. 将前端产物打入 Spring Boot
5. Maven 打包后端
6. 安装 `smart-pig.service` systemd 服务
7. 设置故障自动重启和开机自启
8. 配置 Nginx 反向代理到 `127.0.0.1:8080`

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
```

然后重新部署：

```bash
cd /opt/smart_pig
bash scripts/aliyun-start.sh production
```

在“创意设计 → 3D辅助建模”上传产品参考图，系统会提交Tripo图生3D任务，自动轮询进度，并将完成的GLB模型和预览图下载到本系统资产目录，避免Tripo临时下载地址过期。

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
