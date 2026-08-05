# 之间智造微信小程序端

本目录是基于 **uni-app + Vue 3 + TypeScript** 的微信小程序壳。发布版用户端通过 `web-view` 打开现有 `shixun-vue` 用户端，因此视觉、交互和功能以网页端为唯一来源；原生页面保留为业务域名未配置时的回退入口，不影响 `shixun-vue` 网页端。

## 已实现

- 发布版用户端统一加载网页端 `shixun-vue`，网页端负责登录、创作、作品、充值、商城与退出；原生页面仅作为业务域名异常时的回退入口
- 创作用途强制全屏选择
- 售卖（景区、博物馆）用途选择；用户只需选择省份和该省博物馆，城市与区县作为馆信息展示并保留审批出处上下文
- 文生图（16 积分）、文生 3D（60 积分）、图生 3D（70 积分）入口
- 作品生成任务进度、失败原因、审核状态、审批出处展示；可提交作品审核
- 审核通过的 3D 作品可发起打样或批量生产申请，并查看申请状态
- 原生回退页保留微信小程序 JSAPI 充值（`uni.requestPayment`）和人工收款码兜底；发布版主界面沿用网页端充值流程
- 原生页新增“打样费支付”：审核通过的打样申请可直接调用 `uni.requestPayment`，支付成功只由服务端微信回调确认
- 网页端充值、人工核验、订单历史与刷新均在同一个 web-view 会话内完成
- 通过已配置的 HTTPS 业务域名，在小程序内用 web-view 打开 3D H5 预览；先由 API 签发单资源、5 分钟有效的媒体地址，H5 不会收到登录 JWT
- 作品库支持打开 3D 材质实验室：真实切换 PPC / ABS / PVC / 搪胶软胶 / 全短超柔绒 / 亚克力 / 树脂等 PBR 材质，并导出或保存独立 GLB 材质版本；H5 只持有当前模型 5 分钟有效的受限会话，不能调用普通账户接口
- 用户中心、退出登录

## 本地开发

```bash
cd shixun-miniapp
cp .env.example .env
# 将 VITE_API_BASE_URL 配置为你的 HTTPS API 域名
# 将 VITE_CONSUMER_WEB_URL 配置为同一 HTTPS 业务域名（例如 https://zhijiansk.com/）
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
3. 在微信公众平台配置 `VITE_CONSUMER_WEB_URL` 对应域名为“业务域名”，否则 `<web-view>` 会被微信拦截。
4. 在 `src/manifest.json` 填写真实小程序 AppID。
5. 构建 `shixun-vue` 后会产生 `model-preview.html` 和 `material-lab.html`；生产脚本会将它们和前端一起复制到 Spring Boot 静态目录。推荐设置 `VITE_MODEL_PREVIEW_BASE_URL=https://你的 API 域名/model-preview.html` 及 `VITE_MATERIAL_LAB_BASE_URL=https://你的 API 域名/material-lab.html`，并将该 HTTPS 域名添加为微信小程序业务域名。
6. 小程序支付的 AppID 已配置为 `wxd1ba9e6e01d0e3db`。后端必须配置同一 AppID 的 AppSecret、微信支付商户号、商户 API 证书/API v3 Key、平台证书和公网 HTTPS 回调地址；这些密钥只能放在服务器，不能放进小程序代码或 `.env` 构建产物。
7. 在微信商户平台完成“普通商户 + 小程序 AppID 绑定”，开通 JSAPI/小程序支付，并分别配置支付通知地址 `/api/payments/wechat/notify`、退款通知地址 `/api/payments/wechat/refund-notify`。在公众平台把 API 域名加入 request 合法域名。
8. 后端已提供管理员单笔对账、异常未入账订单原路退款、已到账订单退款、退款状态查询和每日交易/退款/资金账单下载接口；任何 `payment_exception`、`refund_unknown`、`refund_exception` 都要在后台核对后再处理，不能直接给用户补积分。

管理端登录后，在左侧“系统 → 支付运营”可以查看订单、确认人工收款、发起退款、处理异常订单并执行微信日账单对账。

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
