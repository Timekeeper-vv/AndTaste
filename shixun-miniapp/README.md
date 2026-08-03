# 之间智造微信小程序端

本目录是基于 **uni-app + Vue 3 + TypeScript** 的独立微信小程序端，复用现有 Spring Boot API、JWT、积分、博物馆与支付订单数据；不会影响 `shixun-vue` 网页端。

## 已实现

- 账号密码登录，启动时按本地会话恢复到创作工作台；所有业务请求自动携带 JWT Bearer Token
- 创作用途强制全屏选择
- 售卖（景区、博物馆）用途选择；当前测试目录提供博物馆的省 / 市 / 区 / 机构选择，并保留审批出处上下文
- 文生图（16 积分）、文生 3D（60 积分）、图生 3D（70 积分）入口
- 作品生成任务进度、失败原因、审核状态、审批出处展示；可提交作品审核
- 审核通过的 3D 作品可发起打样或批量生产申请，并查看申请状态
- 微信小程序 JSAPI 充值（`uni.requestPayment` 编译为 `wx.requestPayment`），支付结果只认服务端微信回调；未完成配置时自动保留人工收款码兜底
- 人工收款码充值、提交人工核验、订单历史与刷新
- 通过已配置的 HTTPS 业务域名，在小程序内用 web-view 打开 3D H5 预览；先由 API 签发单资源、5 分钟有效的媒体地址，H5 不会收到登录 JWT
- 用户中心、退出登录

## 本地开发

```bash
cd shixun-miniapp
cp .env.example .env
# 将 VITE_API_BASE_URL 配置为你的 HTTPS API 域名
npm install
npm run dev:mp-weixin
```

在微信开发者工具中导入：

```text
shixun-miniapp/dist/build/mp-weixin
```

并在 `src/manifest.json` 中将 `mp-weixin.appid` 改为你的真实小程序 AppID。

## 发布前必须完成

1. 为后端配置公网 **HTTPS 域名**，不要填 `localhost`、内网 IP 或开发端口。
2. 在微信公众平台配置该 HTTPS API 域名为“request 合法域名”。
3. 在 `src/manifest.json` 填写真实小程序 AppID。
4. 构建 `shixun-vue` 后会产生 `model-preview.html`；生产脚本会将它和前端一起复制到 Spring Boot 静态目录。推荐设置 `VITE_MODEL_PREVIEW_BASE_URL=https://你的 API 域名/model-preview.html`，并将该 HTTPS 域名添加为微信小程序业务域名。
5. 小程序支付的 AppID 已配置为 `wxd1ba9e6e01d0e3db`。后端必须配置同一 AppID 的 AppSecret、微信支付商户号、商户 API 证书/API v3 Key、平台证书和公网 HTTPS 回调地址；这些密钥只能放在服务器，不能放进小程序代码或 `.env` 构建产物。
6. 在微信商户平台完成“普通商户 + 小程序 AppID 绑定”，开通 JSAPI/小程序支付，并分别配置支付通知地址 `/api/payments/wechat/notify`、退款通知地址 `/api/payments/wechat/refund-notify`。在公众平台把 API 域名加入 request 合法域名。
7. 后端已提供管理员单笔对账、异常未入账订单原路退款、已到账订单退款、退款状态查询和每日交易/退款/资金账单下载接口；任何 `payment_exception`、`refund_unknown`、`refund_exception` 都要在后台核对后再处理，不能直接给用户补积分。

## 生产环境开启自动支付

复制 `deploy/env.example` 为服务器上的 `.env`，填写 `PAYMENT_WECHAT_*` 全部真实值后执行部署脚本。至少需要：

- `PAYMENT_WECHAT_ENABLED=true`
- `PAYMENT_WECHAT_APP_ID=wxd1ba9e6e01d0e3db`
- 小程序 AppSecret、商户号、商户 API 证书序列号和私钥路径、API v3 Key
- 支付通知和退款通知的公网 HTTPS URL
- 微信支付平台证书文件及其序列号

先在微信支付沙箱/小额真实订单完成一次“下单—支付—回调—到账—退款—退款回调—对账”闭环，再切换正式流量。用户端支付按钮成功不代表到账，必须等订单状态变为 `paid`。

## 构建

```bash
npm run build:mp-weixin
```

产物位于 `dist/build/mp-weixin`，用微信开发者工具上传并提交审核。
