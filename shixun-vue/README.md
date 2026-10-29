# shixun-vue · 之间智造前端

基于 **Vue 3 + TypeScript + Vite** 的前端工程，同时承载 **管理后台**（管理员 / 审批主管 / 员工）和 **网页 C 端**（`ConsumerMobilePage`、活动页、消费侧 API）两套界面。构建产物会被复制到 Spring Boot 静态资源目录，随后端 JAR 一起部署。

## 技术栈

| 技术 | 版本 | 用途 |
|---|---|---|
| Vue | ^3.5 | 组件与组合式 API |
| TypeScript | ^6.0 | 类型约束 |
| Vite | ^8.0 | 开发与构建 |
| ECharts | ^6.1 | 经营仪表盘可视化 |
| Three.js | ^0.183 | 3D 模型网页预览 |
| @google/model-viewer | ^4.3 | 商品 / 作品 3D 展示 |
| xlsx | ^0.18 | 数据导出 |
| jsPDF / jspdf-autotable | ^4 / ^5 | PDF 报价单导出 |
| qrcode | ^1.5 | 网页扫码登录小程序码生成 |

## 主要页面组件

### 管理端（按角色进入）

| 组件 | 说明 |
|---|---|
| `LoginPage.vue` | 账号密码登录 |
| `CreativeDashboard.vue` | 经营看板，订单 / 生产 / 库存 / 物流仪表盘 |
| `CreativeStudio.vue` | 2D / 3D AI 创作工作台 |
| `OrderManagement.vue` | 统一商业订单、报价、打样 / 大货 / 发货 |
| `SampleWorkOrderPage.vue` / `BulkProductionWorkOrderPage.vue` | 打样 / 量产管理 |
| `WarehouseManagement.vue` | 库存与出入库 |
| `LogisticsTracking.vue` | 物流查询与绑定 |
| `ApprovalCenter.vue` | 四人会签审批中心 |
| `ChainApplicationPage.vue` / `FinanceApplicationPage.vue` | 连锁 / 财务申请 |
| `AttendanceManagementPage.vue` / `HumanResourcePage.vue` | 人事与考勤 |
| `SupplierList.vue` + `AiAssistantPage.vue` | 供应商台账与 Text-to-API AI 助手 |
| `UserManagement.vue` | 账号权限 |
| `ConsumerPointsActivities.vue` | 积分活动管理（红包雨 / 题库 / 悬赏令） |

### 网页 C 端（`Consumer*` 前缀）

| 组件 | 说明 |
|---|---|
| `ConsumerMobilePage.vue` | 面向消费者的移动端 H5，包含积分扣费兜底（平面图 120、三视图 450、3D 1680）、充值档位与创作入口 |
| `ConsumerAccountCenter.vue` | 账户中心、交易流水 |
| `ConsumerCreditManagement.vue` | 积分充值与订单 |
| `ConsumerPointsActivities.vue` | 签到 / 红包雨 / 答题 / 悬赏令 |
| `ConsumerWorksLibrary.vue` | 作品库与状态 |
| `ConsumerSubmissionWorkspace.vue` | 悬赏投稿 |
| `ConsumerCommercialWorkspace.vue` | 商业化：报价、打样、量产 |

### 公共能力

- `utils/assetAccess.ts`：AI 生成资产的鉴权下载（后端签短期 URL）
- `utils/consumerApi.ts` / `utils/creativeGeneration.ts`：前端 API 封装
- `utils/miniappBridge.ts`：网页扫码登录对接
- `material-lab.*` / `model-preview.*`：Three.js 模型预览与材质实验室

## 本地开发

```bash
npm install
npm run dev -- --host 0.0.0.0
# http://localhost:5173
```

类型检查 / 构建：

```bash
npm run type-check
npm run build
```

构建产物会输出到 `dist/`；生产部署时，`scripts/aliyun-start.sh` 会把 `dist/` 复制到 `shixun/src/main/resources/static/` 再打包 Spring Boot JAR。

## 与后端联调

所有接口由同仓库的 Spring Boot 后端提供，开发时默认 `http://localhost:8080`。如需切换后端地址，可在 `vite.config.ts` 的 `server.proxy` 或 `.env.development` 中调整。正式域名请在微信公众平台后台加入合法域名列表（若涉及小程序扫码登录）。

## 目录

```
shixun-vue/
├── src/
│   ├── components/        # 所有业务组件（管理端 + C 端）
│   ├── utils/             # 公共工具（API 封装、资产访问、扫码登录桥接）
│   ├── assets/            # 静态资源
│   ├── App.vue
│   └── main.ts
├── vite.config.ts
├── tsconfig.json
└── package.json
```
