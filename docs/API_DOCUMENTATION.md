# 之间智造 / 之间味道文创产品智能体平台接口文档

- 生成日期：2026-08-03
- 后端模块：`shixun`（Spring Boot 2.7.3，Java 17）
- 接口来源：`shixun/src/main/java/com/example/shixun/controller/*Controller.java`
- 默认服务地址：`http://localhost:8080`
- API 统一前缀：`/api`
- Swagger：`/swagger-ui/index.html`，OpenAPI JSON：`/v3/api-docs`
- 路由规模：19 个 Controller，约 215 个接口方法；其中 Tripo 3D 生成存在 2 个等价 URL，因此本文按 216 条 URL 路由列出。

> 说明：本文档以当前源码为准整理。所有涉及金额、支付、退款、版权授权、生产交期、合同承诺的结果仅供系统流程使用，正式对外发送或执行前必须人工复核。

---

## 1. 通用约定

### 1.1 请求与响应

| 项 | 说明 |
|---|---|
| JSON 请求 | 默认 `Content-Type: application/json` |
| 文件上传 | 使用 `multipart/form-data` |
| 日期 | 常见格式：`yyyy-MM-dd`；日期时间常见格式：`yyyy-MM-dd HH:mm:ss` |
| 分页 | 多数分页接口使用 `page` 从 1 开始，`size/pageSize` 有服务端上限 |
| 错误响应 | Spring 默认错误或业务错误 JSON；鉴权失败常见：`{"success":false,"message":"请先登录"}` |

### 1.2 鉴权

除下列公开/回调接口外，`/api/**` 均需携带：

```http
Authorization: Bearer <JWT>
```

公开或外部回调接口：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/users/login` | 登录获取 JWT |
| `POST` | `/api/users` | 未携带 Bearer 时为 C 端用户公开注册；携带管理员 Token 时为后台创建账号 |
| `POST` | `/api/payments/wechat/notify` | 微信支付回调，使用微信签名验签 |
| `POST` | `/api/payments/wechat/refund-notify` | 微信退款回调，使用微信签名验签 |
| `POST` | `/api/logistics/callback/kuaidi100` | 快递100回调，使用回调签名盐校验 |

### 1.3 角色权限

| 角色 | 说明 |
|---|---|
| `admin` | 超级管理员；用户管理、支付管理、审核管理等最高权限 |
| `technician` | 审批主管/后台操作角色，可处理部分管理端接口和审批 |
| `feeder` | 员工/申请提交角色，主要提交申请、查看业务数据 |
| `designer` | 设计师角色，用户体系支持，当前接口中显式权限较少 |
| `user` | C 端消费者/创作者，使用小程序、创作、支付、作品、客服、商城个人订单等接口 |

后台管理类路径由 JWT 过滤器限制为 `admin/technician/feeder`，包括：供应商、仓储、物流、生产、审批、供应链工单、MVP、SaaS、用户管理、后台通知、后台支付管理、C端资产审核等。

---

## 2. 用户认证与账号管理

Base：`/api/users`、`/api/auth`

| 方法 | 路径 | 功能 | 参数/请求体 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `POST` | `/api/users/login` | 用户登录 | `{username,password}` | `token`,`tokenType=Bearer`,`expiresIn`,`user` | 公开 |
| `GET` | `/api/auth/me` | 当前登录用户 | Header Bearer | `{user}`，不返回密码 | 登录 |
| `GET` | `/api/users` | 用户列表/分页 | Query：`search,page,size=10` | 不传 `page` 返回数组；传 `page` 返回分页 | `admin/technician` |
| `GET` | `/api/users/{id}` | 用户详情 | Path：`id` | `User` | `admin/technician` |
| `POST` | `/api/users` | 注册/创建用户 | 见下方 User 字段 | 新用户 | 公开注册或 `admin` |
| `PUT` | `/api/users/{id}` | 更新用户 | `User` | 更新后用户 | `admin` |
| `DELETE` | `/api/users/{id}` | 删除用户 | Path：`id` | `204 No Content` | `admin` |
| `POST` | `/api/users/{id}/reset-password` | 重置密码 | `{password}` | `{id,username,message}` | `admin` |

`User` 字段：`id, username, age, email, phone, password, role`。密码至少 12 位，响应不回显密码。

公开注册 C 端 `user` 时额外必填/必须为真：`agreeDisclaimer, agreeConfidentiality, agreeContentPolicy, realNameAcknowledged, complianceSignature`。公开注册传入的 `role` 会被服务端强制设为 `user`。

---

## 3. AI 总助手与系统搜索

Base：`/api/ai`

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `POST` | `/chat` | AI 业务助手问答 | `{message, history?, currentUser?}` | `reply, source, webSearch?, tool?, toolArguments?, toolResult?` | 登录 |
| `POST` | `/chat/stream` | 文本流式问答 | 同 `/chat` | `text/plain` 逐段输出 | 登录 |
| `GET` | `/search` | 联网搜索 | Query：`q` | `{query,results,text,error?}` | 登录 |

助手会优先尝试工作流、打样工单、供应商结构化工具；涉及“最新/联网/搜索/政策/价格/趋势”等关键词时会走 WebSearchService。

---

## 4. 系统预警通知

Base：`/api/notifications`

| 方法 | 路径 | 功能 | 返回要点 | 权限 |
|---|---|---|---|---|
| `GET` | `/api/notifications` | 获取当前系统待处理预警 | 最多 20 条；含审批、库存、物流、拣货、询盘、报价、配置预警 | 后台角色 |

返回字段通常包含：`type, category, title, message`。

---

## 5. 供应商与银行账户

Base：`/api/suppliers`

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/api/suppliers` | 供应商账户列表 | Query：`search?` | 最多 300 条账户 | 后台角色 |
| `POST` | `/api/suppliers/search` | 结构化搜索供应商 | `SupplierSearchRequest` | `total/items` 或 `count/names` | 后台角色 |
| `POST` | `/api/suppliers/statistics` | 供应商聚合统计 | `SupplierStatisticsRequest` | `field, groups, message?` | 后台角色 |
| `POST` | `/api/suppliers` | 新增供应商账户 | `{receiverNo?,supplier,accountType?,accountName,bankAccount,bank,branch?,location?,note?}` | 新增行 | 后台角色 |
| `DELETE` | `/api/suppliers/{id}` | 删除供应商账户 | Path：`id` | `{deleted}` | 后台角色 |

`SupplierSearchRequest`：`region, keyword, bank_name/bankName, is_count_only/countOnly, limit(1-100)`。  
`SupplierStatisticsRequest`：`group_by_field/groupByField`，支持 `region`、`bank_name`；`include_count/includeCount`。

---

## 6. 审批流 / 工作流中心

Base：`/api/workflows`

当前统一为“四人会签审批”：`审批员1`、`审批员2`、`审批员3`、`审批员4` 全部同意后通过；同一审批员同一轮不能重复审批。

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/definitions` | 审批流定义 | - | `flows, categoryDefaults` | 后台角色 |
| `GET` | `/summary` | 审批统计 | - | `totalCount,pendingCount,...,recentPending,recentApproved` | 后台角色 |
| `GET` | `/applications` | 申请列表 | Query：`status,category,applicant,applicantRole,page,size=20` | 数组或分页 `{content,total,page,size}` | 后台角色；`feeder` 仅自己 |
| `GET` | `/applications/{id}` | 申请详情 | Path：`id` | 申请、字段、流程、日志、时间线 | 后台角色；`feeder` 仅自己 |
| `POST` | `/applications` | 提交申请 | `WorkflowSubmitRequest` | 新申请详情 | `admin/technician/feeder` |
| `POST` | `/applications/{id}/approve` | 同意审批 | `WorkflowActionRequest` | 更新后申请 | `admin/technician`；固定审批员用户名 |
| `POST` | `/applications/{id}/reject` | 驳回审批 | `WorkflowActionRequest` | 更新后申请 | `admin/technician`；固定审批员用户名 |
| `POST` | `/applications/{id}/transfer` | 转交 | `WorkflowActionRequest.target` | 更新后申请 | 非固定会签节点可用 |
| `POST` | `/applications/{id}/withdraw` | 撤回 | `WorkflowActionRequest.comment?` | 更新后申请 | 申请人或 `admin` |
| `POST` | `/applications/{id}/resubmit` | 重新提交 | `WorkflowResubmitRequest` | 更新后申请 | 申请人或 `admin` |
| `GET` | `/notifications` | 审批通知 | Query：`receiver?`（服务端实际使用当前登录人） | 最近 50 条通知 | 后台角色 |

`WorkflowSubmitRequest`：`category,typeKey,title?,flowType?,fields`；旧字段 `applicant/applicantRole` 不被信任，实际以 JWT 为准。  
`WorkflowActionRequest`：`comment,target`；旧字段 `operator/operatorRole` 不被信任。  
常用分类：`finance, production, chain, marketDepartment, projectDepartment, humanResource, attendance`。

---

## 7. 供应链工单：打样申请

Base：`/api/supply-chain/sample-work-orders`

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/verify` | Excel 导入完整性校验 | - | `expectedRows, importedRows, missingRows, complete` | 后台角色 |
| `GET` | `/stats` | 打样统计 | - | 按状态、审批、类型、负责人、部门、月份聚合 | 后台角色 |
| `GET` | `/options` | 筛选项 | - | `statuses,owners,orderTypes,productTypes,departments` | 后台角色 |
| `GET` | `/` | 分页列表 | Query：`keyword,status,owner,orderType,productType,page=1,size=50` | `{items,total,page,size,...}` | 后台角色 |
| `GET` | `/{id}` | 详情 | Path：`id` | 完整打样工单 | 后台角色 |
| `POST` | `/` | 新增打样工单 | 见字段 | 新工单详情 | 后台角色 |
| `PUT` | `/{id}` | 编辑打样工单 | 见字段 | 更新后详情 | 后台角色 |
| `PUT` | `/{id}/work-status` | 更新工单状态 | `{workOrderStatus,startDate?,estimatedCompleteDate?,actualCompleteDate?,owner?,factory?,sampleCostYuan?,sampleFileProvidedDate?}` | 更新后详情 | 后台角色 |
| `POST` | `/{id}/submit-approval` | 提交审批中心 | `{}` | 更新后详情，关联 `workflowApplicationId` | `admin/technician/feeder` |
| `DELETE` | `/{id}` | 软删除 | 可空 body | `{success,id}` | 后台角色 |

新增/编辑字段：`applicationNo?, initiatorDepartment?, applicationDepartment?, projectName?, productName(必填), orderType?, productType?, productSubType?, productEstimate?, productEstimateCurrency?, sampleQuantityText?, specFlavor?, sampleFeeYuan?, detailRemark?, detailProjectName?, linkedProjectFlow?, attachmentSummary?, startDate?, estimatedCompleteDate?, actualCompleteDate?, owner?, factory?, sampleCostYuan?, sampleFileProvidedDate?`。

---

## 8. 供应链工单：大货生产申请

Base：`/api/supply-chain/bulk-production-orders`

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/verify` | Excel 导入完整性校验 | - | `expectedRows, importedRows, missingRows, complete` | 后台角色 |
| `GET` | `/stats` | 大货统计 | - | 按状态、审批、项目、生产类型、品类、负责人、月份聚合 | 后台角色 |
| `GET` | `/options` | 筛选项 | - | `statuses,owners,projectTypes,projectLevels,productionTypes,primaryCategories,departments` | 后台角色 |
| `GET` | `/` | 分页列表 | Query：`keyword,status,owner,projectType,productionType,primaryCategory,page=1,size=50` | `{items,total,page,size,...}` | 后台角色 |
| `GET` | `/{id}` | 详情 | Path：`id` | 完整大货工单 | 后台角色 |
| `POST` | `/` | 新增大货工单 | 见字段 | 新工单详情 | 后台角色 |
| `PUT` | `/{id}` | 编辑大货工单 | 见字段 | 更新后详情 | 后台角色 |
| `PUT` | `/{id}/work-status` | 更新状态 | `{workOrderStatus,startDate?,estimatedCompleteDate?,actualCompleteDate?,owner?,factory?}` | 更新后详情 | 后台角色 |
| `POST` | `/{id}/submit-approval` | 提交审批中心 | `{}` | 更新后详情，关联 `workflowApplicationId` | `admin/technician/feeder` |
| `DELETE` | `/{id}` | 软删除 | 可空 body | `{success,id}` | 后台角色 |

新增/编辑字段：`applicationNo?, initiatorDepartment?, applicationDepartment?, projectName?, projectType?, projectLevel?, projectDetail?, productName(必填), productCode?, primaryCategory?, secondaryCategory?, productionType?, productionQuantity?, specFlavor?, unitPrice?, unitPriceCurrency?, productRemark?, designAttachmentSummary?, linkedApproval?, contractAttachmentSummary?, startDate?, estimatedCompleteDate?, actualCompleteDate?, owner?, factory?`。

---

## 9. 创意 AI、资产库、C 端作品与审核

Base：`/api/creative/ai`

### 9.1 风格与 C 端额度

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/styles` | 创作风格档案 | - | `id,name,basePrompt,negativePrompt,palette,culturalGuardrails` | 登录 |
| `GET` | `/consumer-credits/rules` | 额度消耗规则 | - | `image2d,imageTo3d,textTo3d,modelConvert,unit` | 登录 |
| `GET` | `/consumer-credits/account` | 当前 C 端额度账户 | - | `balance,frozenBalance,totalRecharged,totalConsumed` | `user` |
| `GET` | `/consumer-credits/admin/accounts` | C端额度账户列表 | Query：`search,size=200` | 账户列表 | 创作后台管理员 |
| `GET` | `/consumer-credits/admin/transactions` | 额度流水 | Query：`userId,status,size=300` | 流水列表 | 创作后台管理员 |
| `POST` | `/consumer-credits/admin/recharge` | 管理员充值额度 | `{userId,amount,remark?}` | 账户+`transactionId` | 创作后台管理员 |
| `POST` | `/consumer-credits/admin/set-balance` | 设置目标余额 | `{userId,balance,remark?}` | 账户+`transactionId` | 创作后台管理员 |

### 9.2 Prompt 与 2D 生图

| 方法 | 路径 | 功能 | 请求体 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `POST` | `/prompt/compose` | 按风格档案组合提示词 | `GenerateImageRequest` | `prompt,negativePrompt,styleName,guardrails` | 登录 |
| `POST` | `/prompt/ai` | Qwen/SiliconFlow 产品图提示词 | `GenerateImageRequest` | `prompt,rawPrompt,negativePrompt,styleName,source` | 登录 |
| `POST` | `/prompt/tripo-optimize` | 2D 提示词英文优化 | `GenerateImageRequest` | `prompt,usageGuide,source,target` | 登录 |
| `POST` | `/text-to-image` | SiliconFlow 2D 生图 | `GenerateImageRequest` | `jobNo,assetId,prompt,negativePrompt,status,source,model,imageUrl/previewUrl` | 登录 |
| `POST` | `/image-to-image` | 参考图改图 | `GenerateImageRequest(inputAssetId必填)` | `jobId,jobNo,assetId,status,previewUrl` | 登录；需资产权限 |
| `GET` | `/jimeng/config` | 即梦配置 | - | `configured,provider,model,imageSizes,aspectRatios,...` | 登录 |
| `POST` | `/jimeng/text-to-image` | 即梦 4.6 真实生图 | `GenerateImageRequest` | `jobId,jobNo,assetId,status,provider,model,previewUrl` | 登录；C端消耗额度 |
| `GET` | `/imagen/config` | Google Imagen 4 配置 | - | `configured,provider,model,...` | 登录 |
| `POST` | `/imagen/text-to-image` | Google Imagen 4 生图 | `GenerateImageRequest` | `jobId,jobNo,assetId,status,remoteImage,model,message` | 登录 |
| `GET` | `/modao/config` | 墨刀配置 | - | `configured,workspaceUrl,mcpUrl` | 登录 |
| `POST` | `/modao/launch` | 墨刀 AI 设计图 | `GenerateImageRequest` | `jobId,assetId,taskUrl,previewUrl,message` | 登录 |
| `POST` | `/volcengine/seedream/multiview` | Doubao Seedream 参考图生成四视图 | `MultiViewImageRequest` | `images[{view,label,assetId,previewUrl}],message` | 登录；需资产权限 |

`GenerateImageRequest`：`title,provider,prompt,negativePrompt,styleId,scene,productType,productCategory,material,imageSize,seed,tags,inputAssetId,tripoImageModel,tripoTemplate,tPose,sketchToRender,imagenAspectRatio,imagenImageSize,imagenOutputFormat`。旧字段 `currentUserId` 不作为身份依据。

### 9.3 Tripo 3D / 任务轮询 / 下载

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/tripo/config` | Tripo 配置与余额 | - | `configured,modelOptions,modes,imageModels,balance` | 登录 |
| `POST` | `/prompt/tripo-3d-optimize` | 3D 英文提示词优化 | `Generate3dRequest` | `prompt,negativePrompt,template,templateName,usageTips,target` | 登录 |
| `POST` | `/tripo/text-to-image` | Tripo 文生图异步任务 | `GenerateImageRequest` | `jobId,jobNo,taskId,status,progress,provider,model` | 登录 |
| `GET` | `/tripo/image-tasks/{jobId}` | 轮询 Tripo 文生图 | Path：`jobId` | 任务进度；成功后返回资产信息 | 任务创建人或管理员 |
| `POST` | `/tripo/generate` | Tripo 3D 生成 | `Generate3dRequest` | `jobId,jobNo,taskId,status,modelVersion,qualityPreset` | 登录；C端消耗额度 |
| `POST` | `/tripo/image-to-3d` | 同上别名 | `Generate3dRequest` | 同上 | 登录 |
| `GET` | `/tripo/tasks/{jobId}` | 轮询 Tripo 3D | Path：`jobId` | 任务进度；成功后返回模型资产 | 任务创建人或管理员 |
| `POST` | `/text-to-3d` | SiliconFlow 3D 规格书 | `Generate3dRequest` | `assetId,aiDraft,exportFormats,message` | 登录 |
| `GET` | `/assets/{id}/download-model` | 下载/转换模型 | Query：`format=GLB/OBJ/STL` | 模型文件流；非 GLB 可能消耗 C端转换额度 | 资产拥有者/管理员/短时 token |

`Generate3dRequest`：`mode(image_to_model/multiview_to_model/text_to_model), modelVersion, promptTemplate, prompt, negativePrompt, materialLabel, materialPrompt, productCategory, inputAssetId, multiviewAssetIds{front,left,back,right}, exportFormats, texture, pbr, textureQuality, geometryQuality, textureAlignment, orientation, autoSize, imageAutofix, quad, smartLowPoly, generateParts, exportUv, compress, faceLimit, modelSeed, imageSeed, textureSeed`。

### 9.4 资产库与私有媒体访问

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `POST` | `/assets/upload` | 上传参考图 | Multipart：`file,title?,tags?` | `assetId,title,url,imageUrl,previewUrl` | 登录 |
| `POST` | `/assets/{id}/material-variants` | 保存材质实验室导出的 GLB | Multipart：`file,materialLabel?` | 新模型资产 | `user`，需材质实验室短时 token 或登录 |
| `GET` | `/assets/{id}/content` | 读取图片/文件内容 | Path：`id`，可用 `access_token` | 二进制 | 资产权限或短时 token |
| `GET` | `/assets/{id}/model-content` | 读取 GLB 模型 | Path：`id`，可用 `access_token` | `model/gltf-binary` | 资产权限或短时 token |
| `GET` | `/assets/{id}/preview-content` | 读取预览图 | Path：`id`，可用 `access_token` | 图片二进制 | 资产权限或短时 token |
| `POST` | `/assets/{id}/preview-access` | 签发 5 分钟预览 token | Path：`id` | `accessToken,url,previewUrl,expiresIn=300` | 资产权限 |
| `POST` | `/assets/{id}/material-lab-access` | 签发材质实验室 token | Path：`id` | `modelUrl,accessToken,expiresIn=300` | `user` 且模型资产 |
| `GET` | `/assets` | 资产列表 | Query：`type?,size=100` | 资产列表；媒体 URL 均为短时签名 URL | 登录；非管理员仅自己 |
| `GET` | `/jobs` | AI 任务列表 | - | 任务列表 | 登录；非管理员仅自己 |

### 9.5 C 端作品审核、生产申请、评估与版权咨询

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/consumer-assets/review` | 待审核 C端作品 | Query：`userId,status,size=100` | 作品列表 | 创作后台管理员 |
| `GET` | `/consumer-assets/inventory` | 已通过 C端作品库存 | Query：`userId,type,keyword,size=200` | 作品列表 | 创作后台管理员 |
| `PUT` | `/consumer-assets/{id}/submit-review` | C端提交作品审核 | `{purpose, museumId?, note?}` | `{success,id,status=review}` | `user` |
| `PUT` | `/consumer-assets/{id}/review` | 管理员审核作品 | `{status:approved/rejected/review, comment?}` | 审核结果 | 创作后台管理员 |
| `GET` | `/consumer-production/museums` | 博物馆投放目录 | - | 带推荐策略的博物馆列表 | 登录 |
| `GET` | `/consumer-production/my` | 我的打样/生产申请 | Query：`type=sample/bulk,size=100` | 申请列表 | `user` |
| `POST` | `/consumer-production/submit` | 提交 C端打样/批产申请 | 见下方 | `{success,id,requestNo,status=review}` | `user`，作品需已审核通过 |
| `GET` | `/consumer-production/admin/review` | 管理端生产申请列表 | Query：`type,status,userId,size=200` | 申请列表 | 创作后台管理员 |
| `PUT` | `/consumer-production/admin/{id}/review` | 审核生产申请 | `{status,comment?}` | 审核结果 | 创作后台管理员 |
| `POST` | `/reviews` | 多角色智能评审 | `{assetId,context?}` | `overallScore,recommendation,summary,agents,matrix,roadmap` | 资产权限 |
| `GET` | `/reviews` | 评审记录 | Query：`assetId?` | 评审列表/详情聚合 | 登录；非管理员仅自己 |
| `POST` | `/production-feasibility` | 生产可行性初筛 | `{productCategory,material,prompt}` | `score,level,issues,suggestions,disclaimer` | 登录 |
| `POST` | `/consumer/copyright-consultations` | 版权服务咨询 | `{service,note?,assetId?}` | `{message,status=pending}` | `user` |

`consumer-production/submit` 请求体：`assetId, requestType(sample/bulk), title?, quantity, purpose(personal/museum_sale), selfShipQuantity?, museumDistribution?, recipientName?, recipientPhone?, recipientAddress?, note?`。个人用途必须全部寄送个人；博物馆售卖用途必须选择一个博物馆并全部投放。

---

## 10. C 端文创商城

Base：`/api/creative`

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/dashboard` | 商城运营看板 | - | 作品/SKU/设计师/订单/收入/热门作品/最新订单 | `admin` |
| `GET` | `/categories` | 分类 | - | 分类数组 | 登录 |
| `GET` | `/tags` | 标签 | - | 标签数组 | 登录 |
| `GET` | `/artworks` | 已审核上架作品 | Query：`keyword?,categoryId?` | 作品列表含最低价/SKU数量 | 登录 |
| `GET` | `/artworks/{id}` | 作品详情 | Path：`id` | 作品、标签、SKU；成功后浏览量+1 | 登录 |
| `GET` | `/skus` | SKU 列表 | Query：`artworkId?` | SKU 列表 | 登录 |
| `GET` | `/orders` | 商城订单 | - | 员工看全部；C端看自己的订单 | 登录 |
| `POST` | `/orders` | 创建商城订单 | `CreateOrderRequest` | `orderId,orderNo,payAmount,orderStatus=pending_pay,paymentRequired` | `user` |
| `GET` | `/designers` | 设计师列表 | - | 设计师档案与作品数 | 登录 |

`CreateOrderRequest`：`items:[{skuId,quantity}], paymentMethod(wechat/manual_wechat_qr), remark?`。服务端禁止通过请求体指定 `userId`，库存会在事务内扣减。

---

## 11. C 端客服 / 人工接管

Base：`/api/customer-service`

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `POST` | `/conversations/open` | 打开/恢复我的客服会话 | - | `{conversation,messages}` | `user` |
| `GET` | `/conversations/mine` | 我的客服历史 | - | `{conversation|null,messages}` | `user` |
| `GET` | `/admin/conversations` | 管理端会话列表 | - | 会话、最后消息、未读数 | 后台角色 |
| `POST` | `/admin/conversations/open` | 管理端为用户打开会话 | `{userId}` | `{conversation,messages}` | 后台角色 |
| `GET` | `/conversations/{id}` | 会话详情 | Query：`viewer=staff?` | `{conversation,messages}` | 会话所属 user 或 staff |
| `POST` | `/conversations/{id}/messages` | 发送消息 | `{content}` | `{conversation,messages}`；未人工接管时 AI 自动回复 | user/staff |
| `POST` | `/conversations/{id}/human-takeover` | 人工接管/取消 | `{enabled?}` | 会话详情 | 后台角色 |
| `POST` | `/conversations/{id}/close` | 关闭会话 | - | `{message}` | 后台角色 |

---

## 12. 支付与 C 端额度充值

Base：`/api/payments`

支付原则：微信支付最终到账只信任 API v3 已验签通知和服务端主动对账；小程序 `requestPayment` 成功回调不用于入账。

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/packages` | 充值套餐与可用渠道 | - | `items,channels` | 登录 |
| `POST` | `/wechat/bind` | 小程序 code 绑定 OpenID | `{code}` | `{bound:true,openIdBound:true}` | `user`，需微信 JSAPI 配置 |
| `POST` | `/orders` | 创建充值订单 | `{packageCode,channel}` | 订单视图；JSAPI 含 `paymentParams` | `user` |
| `GET` | `/orders/{orderNo}` | 查询我的充值订单 | Path：`orderNo` | 订单视图 | `user` |
| `GET` | `/orders` | 我的充值订单历史 | Query：`limit?` | `{items,total,limit}` | `user` |
| `POST` | `/orders/{orderNo}/manual-complete` | 用户提交人工核验 | - | 订单视图 | `user`，手工二维码订单 |
| `POST` | `/orders/{orderNo}/close` | 关闭待支付订单 | - | 订单视图 | `user` |
| `POST` | `/wechat/notify` | 微信支付回调 | 微信头 + 原始 body | `{code,message}` | 微信回调验签 |
| `POST` | `/wechat/refund-notify` | 微信退款回调 | 微信头 + 原始 body | `{code,message}` | 微信回调验签 |
| `GET` | `/admin/orders` | 管理端充值订单 | - | 最近 300 条 | `admin` |
| `POST` | `/admin/orders/{orderNo}/confirm` | 管理员确认手工收款 | - | 订单视图并入账额度 | `admin` |
| `POST` | `/admin/orders/{orderNo}/refund` | 发起原路退款 | `{reason?}` | 退款视图/微信结果 | `admin` |
| `POST` | `/admin/orders/{orderNo}/exception-refund` | 异常未入账订单退款 | `{reason?}` | 退款视图/微信结果 | `admin` |
| `POST` | `/admin/orders/{orderNo}/reconcile` | 单笔微信支付/退款对账 | - | 管理订单视图 | `admin` |
| `POST` | `/admin/reconciliation/daily` | 下载并比对微信日账单 | Query：`billDate=yyyy-MM-dd` | 交易/退款/资金账单结果 | `admin` |
| `GET` | `/admin/reconciliation/daily` | 日账单对账历史 | Query：`billDate?` | 历史记录 | `admin` |
| `GET` | `/admin/exceptions` | 支付异常列表 | - | 异常订单/退款 | `admin` |

套餐：`credit_100(¥9.90/100点)`、`credit_500(¥39.90/500点)`、`credit_1000(¥69.90/1000点)`。  
渠道：`manual_wechat_qr`、`wechat`、`wechat_jsapi`。  
订单常见状态：`pending, manual_review, paid, expired, closed, failed, payment_exception, refund_requested, refund_processing, refund_unknown, refund_exception, refunded, refund_failed`。

---

## 13. 商业 MVP：询盘、报价、BOM、生产、仓储闭环

Base：`/api/mvp`

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/dashboard` | 商业闭环看板 | - | 客户/询盘/报价/BOM/打样/报价金额 | 后台角色 |
| `GET` | `/customers` | 客户列表 | Query：`keyword?` | 客户列表 | 后台角色 |
| `POST` | `/customers` | 新增客户 | `CustomerRequest` | 客户详情 | 后台角色 |
| `GET` | `/inquiries` | 询盘列表 | Query：`status?,keyword?` | 询盘列表 | 后台角色 |
| `GET` | `/inquiries/{id}` | 询盘详情 | Path：`id` | 询盘、报价、关联闭环链接 | 后台角色 |
| `POST` | `/inquiries` | 新增询盘并 AI 分析 | `InquiryRequest` | 询盘详情 | 后台角色 |
| `POST` | `/inquiries/{id}/quote` | 根据询盘生成报价 | `QuoteOptions?` | 报价详情 | 后台角色 |
| `GET` | `/quotes` | 报价列表 | - | 报价列表 | 后台角色 |
| `GET` | `/quotes/{id}` | 报价详情 | Path：`id` | 报价、条款、闭环链接 | 后台角色 |
| `POST` | `/quotes/{id}/status` | 修改报价状态 | `{status}` | 报价详情 | 后台角色 |
| `POST` | `/quotes/{id}/bom-sample` | 由报价生成 BOM 与打样单 | - | `bomId,sampleId,sampleNo,message` | 后台角色 |
| `POST` | `/quotes/{id}/production` | 由报价生成生产单 | - | `productionId,productionNo,estimatedCost,message` | 后台角色 |
| `POST` | `/quotes/{id}/warehouse-inbound` | 完工入库 | - | `inboundId,inboundNo,inventoryId,message` | 后台角色 |
| `POST` | `/quotes/{id}/warehouse-outbound` | 创建出库与拣货 | - | `outboundId,outboundNo,pickNo,message` | 后台角色 |
| `GET` | `/cost-library` | 成本库 | - | `materials,processes,productTypes` | 后台角色 |
| `POST` | `/design-plan` | AI 新品企划 | `DesignPlanRequest` | `planNo,source,aiDraft,theme` | 后台角色 |
| `POST` | `/design-review` | AI 方案评审 | `DesignReviewRequest` | `reviewNo,source,aiDraft` | 后台角色 |

`CustomerRequest`：`name,contactName,phone,wechat,company,industry,source,notes`。  
`InquiryRequest`：`customerId/customerName, contactName, phone, wechat, company, title, productName, productType, quantity, size, material, packaging, destination, deadline, budget, usageScenario, rawRequirement`。  
`QuoteOptions`：`designFee, overheadRate, targetMarginRate`。

---

## 14. 生产管理：BOM、成本核算、统一商业订单

Base：`/api/production`

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/dashboard` | 生产看板 | - | BOM/物料/工艺/打样/生产/采购建议数量 | 后台角色 |
| `GET` | `/materials` | 物料库 | - | 物料列表 | 后台角色 |
| `GET` | `/processes` | 工艺库 | - | 工艺列表 | 后台角色 |
| `GET` | `/boms` | BOM 列表 | - | BOM 含材料/工艺明细 | 后台角色 |
| `GET` | `/boms/{id}` | BOM 详情 | Path：`id` | BOM 明细 | 后台角色 |
| `POST` | `/boms/auto` | 自动创建 BOM | `AutoBomRequest` | BOM 详情 | 后台角色 |
| `PUT` | `/boms/{id}` | 保存/编辑 BOM | `BomEditRequest` | BOM 详情 | 后台角色 |
| `POST` | `/boms/{id}/ai-plan` | AI 生成 BOM/工艺建议 | `{quantity?,mode?,requirement?}` | `materials,processes,budgetAdvice,riskAdvice,model` | 后台角色 |
| `POST` | `/quotes/simulate` | 成本预算模拟 | `QuoteRequest` | 单价/总成本/建议售价/毛利/BOM项 | 后台角色 |
| `GET` | `/quotes` | 成本报价记录 | - | 最近 50 条 | 后台角色 |
| `POST` | `/samples` | 创建打样单 | `SampleRequest` | `sampleNo,estimatedCost,status` | 后台角色 |
| `GET` | `/samples` | 打样单列表 | - | 打样列表 | 后台角色 |
| `POST` | `/orders` | 创建生产订单 | `ProductionRequest` | `productionNo,productionId,estimatedCost,status` | 后台角色 |
| `GET` | `/orders` | 生产订单列表 | - | 生产订单列表 | 后台角色 |
| `GET` | `/workbench` | 成本核算工作台 | - | `sources,orders`，含审批状态 | 后台角色 |
| `POST` | `/commercial-orders` | 创建统一商业订单 | `CommercialOrderRequest` | 订单详情 | 后台角色 |
| `GET` | `/commercial-orders/{id}` | 商业订单详情 | Path：`id` | 订单、快照、报价、材料工艺 | 后台角色 |
| `POST` | `/commercial-orders/{id}/approval-request` | 提交订单确认/下达生产审批 | `OrderApprovalRequest` | 关联审批申请 | `admin/technician/feeder` |
| `POST` | `/commercial-orders/{id}/confirm` | 客户确认订单 | - | 订单详情；要求 confirm 审批通过 | 后台角色 |
| `POST` | `/commercial-orders/{id}/start` | 下达生产 | - | 创建打样或生产任务；要求 start 审批通过 | 后台角色 |
| `POST` | `/commercial-orders/{id}/ready` | 标记待发货 | - | 订单详情 | 后台角色 |
| `GET` | `/purchase-suggestions` | 采购建议 | - | 采购建议列表 | 后台角色 |

核心 DTO：

- `AutoBomRequest`：`productName,productType,skuId,assetId,plannedQty,targetPrice`
- `QuoteRequest`：`bomId,quantity,overheadRate,targetMarginRate`
- `SampleRequest`：`bomId,assetId,sampleQty,dueDate,feedback`
- `ProductionRequest`：`bomId,plannedQty,startDate,dueDate`
- `BomEditRequest`：`productName,plannedQty,targetPrice,remark,materials[{materialId,qty,lossRate,remark}],processes[{processId,qty,remark}]`
- `CommercialOrderRequest`：`orderType(sample/bulk),projectId,projectSkuId,bomId,quantity,unitPrice,targetMarginRate,customerName,contactName,contactPhone,receiverAddress,productionRequirement`
- `OrderApprovalRequest`：`action(confirm/start), comment?`；旧 `applicant/applicantRole` 不被信任。

---

## 15. 仓储管理

Base：`/api/warehouse`

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/dashboard` | 仓储看板 | - | 产品/库存/入库/出库/待拣/预警统计 | 后台角色 |
| `GET` | `/locations` | 库位列表 | - | 库位 | 后台角色 |
| `GET` | `/inventory` | 库存台账 | - | 库存、库位、产品目录价格等 | 后台角色 |
| `GET` | `/products` | 产品目录 | Query：`keyword,primaryCategory,secondaryCategory,page=1,pageSize=100` | `{total,page,pageSize,items,primaryCategories,secondaryCategories}` | 后台角色 |
| `GET` | `/inbound` | 入库单列表 | - | 入库单及 items | 后台角色 |
| `GET` | `/outbound` | 出库单列表 | - | 出库单及 items | 后台角色 |
| `GET` | `/pick-tasks` | 拣货任务 | - | 拣货任务列表 | 后台角色 |
| `GET` | `/alerts` | 库存预警 | - | open 预警列表 | 后台角色 |
| `POST` | `/inbound` | 入库 | `InboundRequest` | `inboundId,inboundNo,inventoryId,message` | 后台角色 |
| `POST` | `/outbound` | 出库并生成拣货任务 | `OutboundRequest` | `outboundId,outboundNo,pickNo,message` | 后台角色 |
| `POST` | `/pick-tasks/{id}/complete` | 完成拣货扣减库存 | Path：`id` | `{message}` | 后台角色 |
| `POST` | `/alerts/refresh` | 刷新库存预警 | - | `{message,alertCount}` | 后台角色 |
| `POST` | `/alerts/ai-report` | AI 仓储报告 | - | `reportNo,source,report` | 后台角色 |

`InboundRequest`：`itemType,itemCode,itemName,spec,unit,qty,unitCost,locationCode,safetyStock,maxStock,sourceType,supplier,remark`；`operator` 旧字段忽略，以 JWT 用户为准。  
`OutboundRequest`：`inventoryId,qty,orderNo,purpose,receiver`；`operator` 旧字段忽略。

---

## 16. 物流跟踪

Base：`/api/logistics`

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/provider-status` | 快递100配置状态 | - | `queryConfigured,subscribeConfigured,callbackUrl,message` | 后台角色 |
| `GET` | `/dashboard` | 物流看板 | - | 发货、在途、签收、异常数量 | 后台角色 |
| `GET` | `/carriers` | 快递公司 | - | 承运商列表 | 后台角色 |
| `GET` | `/orders` | 可发货商业订单 | - | ready_to_ship/shipped/completed 订单 | 后台角色 |
| `GET` | `/shipments` | 发货单列表 | - | 发货单列表 | 后台角色 |
| `GET` | `/shipments/{id}` | 发货单详情 | Path：`id` | 发货单、轨迹、建议 | 后台角色 |
| `POST` | `/shipments` | 创建发货单 | `ShipmentRequest` | 发货单详情 | 后台角色 |
| `POST` | `/shipments/{id}/sync` | 主动同步快递100轨迹 | Path：`id` | 发货单详情 | 后台角色 |
| `POST` | `/shipments/{id}/subscribe` | 订阅快递100推送 | Path：`id` | 发货单详情 | 后台角色 |
| `POST` | `/shipments/{id}/exception` | 人工标记异常 | `{reason?}` | 发货单详情 | 后台角色 |
| `POST` | `/callback/kuaidi100` | 快递100回调 | Form：`param,sign` | `{result,returnCode,message}` | 外部回调验签 |

`ShipmentRequest`：`orderId,orderNo,receiverName,receiverPhone,receiverAddress,carrierCode,trackingNo`。`carrierCode` 使用快递100编码，如 `shunfeng, zhongtong, yuantong`。

---

## 17. SaaS 规模化、模板市场、项目工作流

Base：`/api/scale`

| 方法 | 路径 | 功能 | 请求/参数 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `GET` | `/dashboard` | SaaS 看板 | - | 租户、订阅、项目、模板、MRR | 后台角色 |
| `GET` | `/tenants` | 租户列表 | - | 租户+套餐订阅 | 后台角色 |
| `POST` | `/tenants` | 创建试用租户 | `TenantRequest` | 租户详情 | 后台角色 |
| `GET` | `/plans` | 套餐列表 | - | SaaS 套餐 | 后台角色 |
| `POST` | `/subscriptions` | 开通/续订套餐 | `SubscribeRequest` | 租户详情 | 后台角色 |
| `GET` | `/usage` | 用量记录 | Query：`tenantId?` | 用量列表 | 后台角色 |
| `GET` | `/templates` | 模板市场 | Query：`category?` | 已发布模板 | 后台角色 |
| `POST` | `/templates/{id}/use` | 使用模板 | `UseTemplateRequest?` | 模板详情，usage+1 | 后台角色 |
| `GET` | `/projects` | 项目列表 | Query：`tenantId?` | 项目列表 | 后台角色 |
| `GET` | `/projects/{id}` | 项目详情 | Path：`id` | 项目+SKU | 后台角色 |
| `POST` | `/projects` | 创建项目 | `ProjectRequest` | 项目详情 | 后台角色 |
| `POST` | `/projects/{id}/ai-plan` | AI 项目计划 | Path：`id` | 项目详情，写入 `aiPlan` | 后台角色 |
| `POST` | `/projects/{id}/generate-skus` | 生成默认 SKU 矩阵 | Path：`id` | 项目详情 | 后台角色 |
| `POST` | `/projects/{id}/ai-review` | AI 项目评审 | Path：`id` | 项目详情，写入 `aiReview` | 后台角色 |
| `POST` | `/project-skus/{skuId}/bom-sample` | SKU 生成 BOM 与打样单 | Path：`skuId` | `bomId,sampleId,sampleNo` | 后台角色 |
| `POST` | `/project-skus/{skuId}/production` | SKU 生成生产单 | Path：`skuId` | `productionOrderId,productionNo` | 后台角色 |

DTO：`TenantRequest{name,industry,contactName,phone,planId}`，`SubscribeRequest{tenantId,planId,months}`，`UseTemplateRequest{tenantId}`，`ProjectRequest{tenantId,inquiryId,quoteId,name,theme,targetAudience,productTypes,budget}`。

---

## 18. 业务 AI 助手与营销文案

### 18.1 商务助手

Base：`/api/creative/assistant`

| 方法 | 路径 | 功能 | 请求体 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `POST` | `/quote` | AI/规则报价助手 | `QuoteAssistantRequest` | 成本、建议价、底价、交期、问题、风险、客户回复、AI稿 | 后台角色 |
| `POST` | `/planning` | 新品企划助手 | `PlanningRequest` | 定位、概念、路线图、KPI、风险、AI稿 | 后台角色 |
| `POST` | `/logistics` | 物流履约预测 | `LogisticsRequest` | 运费、发货日、ETA、承运方案、风险、话术 | 后台角色 |
| `GET` | `/finance` | 经营分析 | - | 收入、订单、SKU、库存、报价毛利、AI分析 | 后台角色 |

`QuoteAssistantRequest`：`productName,productType,quantity,size,material,packaging,destination,designFee,overheadRate,targetMarginRate`。  
`PlanningRequest`：`theme,audience,channel,budget`。  
`LogisticsRequest`：`orderNo,productType,quantity,destination`。

### 18.2 营销文案

Base：`/api/creative/marketing`

| 方法 | 路径 | 功能 | 请求体 | 返回要点 | 权限 |
|---|---|---|---|---|---|
| `POST` | `/copy` | 产品营销文案生成 | `MarketingCopyRequest` | 标题、卖点、详情页、小红书、抖音脚本、客服回复、话题、AI稿 | 后台角色 |

`MarketingCopyRequest`：`brandName,productName,productType,audience,sellingPoints,scenario,priceBand,channel,tone,useAi`。

---

## 19. 完整 URL 路由清单（按 Controller）

> 该清单用于核对覆盖范围；详细请求体与业务说明见前文各模块。

| Controller | Method | Path |
|---|---:|---|
| AiChatController | POST | `/api/ai/chat` |
| AiChatController | POST | `/api/ai/chat/stream` |
| AiChatController | GET | `/api/ai/search` |
| AuthController | GET | `/api/auth/me` |
| CommercialMvpController | GET | `/api/mvp/dashboard` |
| CommercialMvpController | GET | `/api/mvp/customers` |
| CommercialMvpController | POST | `/api/mvp/customers` |
| CommercialMvpController | GET | `/api/mvp/inquiries` |
| CommercialMvpController | GET | `/api/mvp/inquiries/{id}` |
| CommercialMvpController | POST | `/api/mvp/inquiries` |
| CommercialMvpController | POST | `/api/mvp/inquiries/{id}/quote` |
| CommercialMvpController | GET | `/api/mvp/quotes` |
| CommercialMvpController | GET | `/api/mvp/quotes/{id}` |
| CommercialMvpController | POST | `/api/mvp/quotes/{id}/status` |
| CommercialMvpController | POST | `/api/mvp/quotes/{id}/bom-sample` |
| CommercialMvpController | POST | `/api/mvp/quotes/{id}/production` |
| CommercialMvpController | POST | `/api/mvp/quotes/{id}/warehouse-inbound` |
| CommercialMvpController | POST | `/api/mvp/quotes/{id}/warehouse-outbound` |
| CommercialMvpController | GET | `/api/mvp/cost-library` |
| CommercialMvpController | POST | `/api/mvp/design-plan` |
| CommercialMvpController | POST | `/api/mvp/design-review` |
| CreativeAiController | GET | `/api/creative/ai/styles` |
| CreativeAiController | GET | `/api/creative/ai/consumer-credits/rules` |
| CreativeAiController | GET | `/api/creative/ai/consumer-credits/account` |
| CreativeAiController | GET | `/api/creative/ai/consumer-credits/admin/accounts` |
| CreativeAiController | GET | `/api/creative/ai/consumer-credits/admin/transactions` |
| CreativeAiController | POST | `/api/creative/ai/consumer-credits/admin/recharge` |
| CreativeAiController | POST | `/api/creative/ai/consumer-credits/admin/set-balance` |
| CreativeAiController | POST | `/api/creative/ai/prompt/compose` |
| CreativeAiController | POST | `/api/creative/ai/prompt/ai` |
| CreativeAiController | POST | `/api/creative/ai/prompt/tripo-3d-optimize` |
| CreativeAiController | POST | `/api/creative/ai/prompt/tripo-optimize` |
| CreativeAiController | POST | `/api/creative/ai/text-to-image` |
| CreativeAiController | POST | `/api/creative/ai/volcengine/seedream/multiview` |
| CreativeAiController | POST | `/api/creative/ai/image-to-image` |
| CreativeAiController | GET | `/api/creative/ai/jimeng/config` |
| CreativeAiController | POST | `/api/creative/ai/jimeng/text-to-image` |
| CreativeAiController | GET | `/api/creative/ai/imagen/config` |
| CreativeAiController | POST | `/api/creative/ai/imagen/text-to-image` |
| CreativeAiController | GET | `/api/creative/ai/modao/config` |
| CreativeAiController | POST | `/api/creative/ai/modao/launch` |
| CreativeAiController | GET | `/api/creative/ai/tripo/config` |
| CreativeAiController | POST | `/api/creative/ai/tripo/text-to-image` |
| CreativeAiController | GET | `/api/creative/ai/tripo/image-tasks/{jobId}` |
| CreativeAiController | POST | `/api/creative/ai/tripo/generate` |
| CreativeAiController | POST | `/api/creative/ai/tripo/image-to-3d` |
| CreativeAiController | GET | `/api/creative/ai/tripo/tasks/{jobId}` |
| CreativeAiController | POST | `/api/creative/ai/text-to-3d` |
| CreativeAiController | POST | `/api/creative/ai/assets/upload` |
| CreativeAiController | POST | `/api/creative/ai/assets/{id}/material-variants` |
| CreativeAiController | GET | `/api/creative/ai/assets/{id}/content` |
| CreativeAiController | GET | `/api/creative/ai/assets/{id}/model-content` |
| CreativeAiController | GET | `/api/creative/ai/assets/{id}/preview-content` |
| CreativeAiController | POST | `/api/creative/ai/assets/{id}/preview-access` |
| CreativeAiController | POST | `/api/creative/ai/assets/{id}/material-lab-access` |
| CreativeAiController | GET | `/api/creative/ai/assets/{id}/download-model` |
| CreativeAiController | GET | `/api/creative/ai/assets` |
| CreativeAiController | GET | `/api/creative/ai/consumer-assets/review` |
| CreativeAiController | GET | `/api/creative/ai/consumer-assets/inventory` |
| CreativeAiController | PUT | `/api/creative/ai/consumer-assets/{id}/submit-review` |
| CreativeAiController | PUT | `/api/creative/ai/consumer-assets/{id}/review` |
| CreativeAiController | GET | `/api/creative/ai/consumer-production/museums` |
| CreativeAiController | GET | `/api/creative/ai/consumer-production/my` |
| CreativeAiController | POST | `/api/creative/ai/consumer-production/submit` |
| CreativeAiController | GET | `/api/creative/ai/consumer-production/admin/review` |
| CreativeAiController | PUT | `/api/creative/ai/consumer-production/admin/{id}/review` |
| CreativeAiController | POST | `/api/creative/ai/reviews` |
| CreativeAiController | GET | `/api/creative/ai/reviews` |
| CreativeAiController | GET | `/api/creative/ai/jobs` |
| CreativeAiController | POST | `/api/creative/ai/production-feasibility` |
| CreativeAiController | POST | `/api/creative/ai/consumer/copyright-consultations` |
| CreativeBusinessAiController | POST | `/api/creative/assistant/quote` |
| CreativeBusinessAiController | POST | `/api/creative/assistant/planning` |
| CreativeBusinessAiController | POST | `/api/creative/assistant/logistics` |
| CreativeBusinessAiController | GET | `/api/creative/assistant/finance` |
| CreativeMarketplaceController | GET | `/api/creative/dashboard` |
| CreativeMarketplaceController | GET | `/api/creative/categories` |
| CreativeMarketplaceController | GET | `/api/creative/tags` |
| CreativeMarketplaceController | GET | `/api/creative/artworks` |
| CreativeMarketplaceController | GET | `/api/creative/artworks/{id}` |
| CreativeMarketplaceController | GET | `/api/creative/skus` |
| CreativeMarketplaceController | GET | `/api/creative/orders` |
| CreativeMarketplaceController | POST | `/api/creative/orders` |
| CreativeMarketplaceController | GET | `/api/creative/designers` |
| CustomerServiceController | POST | `/api/customer-service/conversations/open` |
| CustomerServiceController | GET | `/api/customer-service/conversations/mine` |
| CustomerServiceController | GET | `/api/customer-service/admin/conversations` |
| CustomerServiceController | POST | `/api/customer-service/admin/conversations/open` |
| CustomerServiceController | GET | `/api/customer-service/conversations/{id}` |
| CustomerServiceController | POST | `/api/customer-service/conversations/{id}/messages` |
| CustomerServiceController | POST | `/api/customer-service/conversations/{id}/human-takeover` |
| CustomerServiceController | POST | `/api/customer-service/conversations/{id}/close` |
| LogisticsController | GET | `/api/logistics/provider-status` |
| LogisticsController | GET | `/api/logistics/dashboard` |
| LogisticsController | GET | `/api/logistics/carriers` |
| LogisticsController | GET | `/api/logistics/orders` |
| LogisticsController | GET | `/api/logistics/shipments` |
| LogisticsController | GET | `/api/logistics/shipments/{id}` |
| LogisticsController | POST | `/api/logistics/shipments` |
| LogisticsController | POST | `/api/logistics/shipments/{id}/sync` |
| LogisticsController | POST | `/api/logistics/shipments/{id}/subscribe` |
| LogisticsController | POST | `/api/logistics/shipments/{id}/exception` |
| LogisticsController | POST | `/api/logistics/callback/kuaidi100` |
| MarketingCopyController | POST | `/api/creative/marketing/copy` |
| NotificationController | GET | `/api/notifications` |
| PaymentController | GET | `/api/payments/packages` |
| PaymentController | POST | `/api/payments/wechat/bind` |
| PaymentController | POST | `/api/payments/orders` |
| PaymentController | GET | `/api/payments/orders/{orderNo}` |
| PaymentController | GET | `/api/payments/orders` |
| PaymentController | POST | `/api/payments/wechat/notify` |
| PaymentController | POST | `/api/payments/wechat/refund-notify` |
| PaymentController | POST | `/api/payments/orders/{orderNo}/manual-complete` |
| PaymentController | POST | `/api/payments/admin/orders/{orderNo}/confirm` |
| PaymentController | POST | `/api/payments/admin/orders/{orderNo}/refund` |
| PaymentController | POST | `/api/payments/admin/orders/{orderNo}/exception-refund` |
| PaymentController | POST | `/api/payments/admin/orders/{orderNo}/reconcile` |
| PaymentController | POST | `/api/payments/admin/reconciliation/daily` |
| PaymentController | GET | `/api/payments/admin/reconciliation/daily` |
| PaymentController | GET | `/api/payments/admin/exceptions` |
| PaymentController | GET | `/api/payments/admin/orders` |
| PaymentController | POST | `/api/payments/orders/{orderNo}/close` |
| ProductionManagementController | GET | `/api/production/dashboard` |
| ProductionManagementController | GET | `/api/production/materials` |
| ProductionManagementController | GET | `/api/production/processes` |
| ProductionManagementController | GET | `/api/production/boms` |
| ProductionManagementController | GET | `/api/production/boms/{id}` |
| ProductionManagementController | POST | `/api/production/boms/auto` |
| ProductionManagementController | POST | `/api/production/quotes/simulate` |
| ProductionManagementController | GET | `/api/production/quotes` |
| ProductionManagementController | POST | `/api/production/samples` |
| ProductionManagementController | GET | `/api/production/samples` |
| ProductionManagementController | POST | `/api/production/orders` |
| ProductionManagementController | GET | `/api/production/orders` |
| ProductionManagementController | GET | `/api/production/workbench` |
| ProductionManagementController | POST | `/api/production/boms/{id}/ai-plan` |
| ProductionManagementController | PUT | `/api/production/boms/{id}` |
| ProductionManagementController | POST | `/api/production/commercial-orders` |
| ProductionManagementController | GET | `/api/production/commercial-orders/{id}` |
| ProductionManagementController | POST | `/api/production/commercial-orders/{id}/confirm` |
| ProductionManagementController | POST | `/api/production/commercial-orders/{id}/start` |
| ProductionManagementController | POST | `/api/production/commercial-orders/{id}/approval-request` |
| ProductionManagementController | POST | `/api/production/commercial-orders/{id}/ready` |
| ProductionManagementController | GET | `/api/production/purchase-suggestions` |
| ScaleUpController | GET | `/api/scale/dashboard` |
| ScaleUpController | GET | `/api/scale/tenants` |
| ScaleUpController | POST | `/api/scale/tenants` |
| ScaleUpController | GET | `/api/scale/plans` |
| ScaleUpController | POST | `/api/scale/subscriptions` |
| ScaleUpController | GET | `/api/scale/usage` |
| ScaleUpController | GET | `/api/scale/templates` |
| ScaleUpController | POST | `/api/scale/templates/{id}/use` |
| ScaleUpController | GET | `/api/scale/projects` |
| ScaleUpController | GET | `/api/scale/projects/{id}` |
| ScaleUpController | POST | `/api/scale/projects` |
| ScaleUpController | POST | `/api/scale/projects/{id}/ai-plan` |
| ScaleUpController | POST | `/api/scale/projects/{id}/generate-skus` |
| ScaleUpController | POST | `/api/scale/projects/{id}/ai-review` |
| ScaleUpController | POST | `/api/scale/project-skus/{skuId}/bom-sample` |
| ScaleUpController | POST | `/api/scale/project-skus/{skuId}/production` |
| SupplierController | GET | `/api/suppliers` |
| SupplierController | POST | `/api/suppliers/search` |
| SupplierController | POST | `/api/suppliers/statistics` |
| SupplierController | POST | `/api/suppliers` |
| SupplierController | DELETE | `/api/suppliers/{id}` |
| SupplyChainBulkProductionController | GET | `/api/supply-chain/bulk-production-orders/verify` |
| SupplyChainBulkProductionController | GET | `/api/supply-chain/bulk-production-orders/stats` |
| SupplyChainBulkProductionController | GET | `/api/supply-chain/bulk-production-orders/options` |
| SupplyChainBulkProductionController | GET | `/api/supply-chain/bulk-production-orders` |
| SupplyChainBulkProductionController | GET | `/api/supply-chain/bulk-production-orders/{id}` |
| SupplyChainBulkProductionController | POST | `/api/supply-chain/bulk-production-orders` |
| SupplyChainBulkProductionController | PUT | `/api/supply-chain/bulk-production-orders/{id}` |
| SupplyChainBulkProductionController | PUT | `/api/supply-chain/bulk-production-orders/{id}/work-status` |
| SupplyChainBulkProductionController | POST | `/api/supply-chain/bulk-production-orders/{id}/submit-approval` |
| SupplyChainBulkProductionController | DELETE | `/api/supply-chain/bulk-production-orders/{id}` |
| SupplyChainSampleWorkOrderController | GET | `/api/supply-chain/sample-work-orders/verify` |
| SupplyChainSampleWorkOrderController | GET | `/api/supply-chain/sample-work-orders/stats` |
| SupplyChainSampleWorkOrderController | GET | `/api/supply-chain/sample-work-orders/options` |
| SupplyChainSampleWorkOrderController | GET | `/api/supply-chain/sample-work-orders` |
| SupplyChainSampleWorkOrderController | GET | `/api/supply-chain/sample-work-orders/{id}` |
| SupplyChainSampleWorkOrderController | POST | `/api/supply-chain/sample-work-orders` |
| SupplyChainSampleWorkOrderController | PUT | `/api/supply-chain/sample-work-orders/{id}` |
| SupplyChainSampleWorkOrderController | PUT | `/api/supply-chain/sample-work-orders/{id}/work-status` |
| SupplyChainSampleWorkOrderController | POST | `/api/supply-chain/sample-work-orders/{id}/submit-approval` |
| SupplyChainSampleWorkOrderController | DELETE | `/api/supply-chain/sample-work-orders/{id}` |
| UserController | GET | `/api/users` |
| UserController | GET | `/api/users/{id}` |
| UserController | POST | `/api/users` |
| UserController | PUT | `/api/users/{id}` |
| UserController | DELETE | `/api/users/{id}` |
| UserController | POST | `/api/users/{id}/reset-password` |
| UserController | POST | `/api/users/login` |
| WarehouseController | GET | `/api/warehouse/dashboard` |
| WarehouseController | GET | `/api/warehouse/locations` |
| WarehouseController | GET | `/api/warehouse/inventory` |
| WarehouseController | GET | `/api/warehouse/products` |
| WarehouseController | GET | `/api/warehouse/inbound` |
| WarehouseController | GET | `/api/warehouse/outbound` |
| WarehouseController | GET | `/api/warehouse/pick-tasks` |
| WarehouseController | GET | `/api/warehouse/alerts` |
| WarehouseController | POST | `/api/warehouse/inbound` |
| WarehouseController | POST | `/api/warehouse/outbound` |
| WarehouseController | POST | `/api/warehouse/pick-tasks/{id}/complete` |
| WarehouseController | POST | `/api/warehouse/alerts/refresh` |
| WarehouseController | POST | `/api/warehouse/alerts/ai-report` |
| WorkflowController | GET | `/api/workflows/definitions` |
| WorkflowController | GET | `/api/workflows/summary` |
| WorkflowController | GET | `/api/workflows/applications` |
| WorkflowController | GET | `/api/workflows/applications/{id}` |
| WorkflowController | POST | `/api/workflows/applications` |
| WorkflowController | POST | `/api/workflows/applications/{id}/approve` |
| WorkflowController | POST | `/api/workflows/applications/{id}/reject` |
| WorkflowController | POST | `/api/workflows/applications/{id}/transfer` |
| WorkflowController | POST | `/api/workflows/applications/{id}/withdraw` |
| WorkflowController | POST | `/api/workflows/applications/{id}/resubmit` |
| WorkflowController | GET | `/api/workflows/notifications` |

---

## 20. 重要安全与实现备注

1. 旧版请求体中的 `currentUserId`、`currentUser`、`operator`、`applicant`、`applicantRole` 等身份字段仅为兼容旧前端，后端实际以 JWT 过滤器写入的服务端 Claims 为准。
2. 私有创作资产不应直接暴露 `/uploads` 或 `/generated` 路径；前端应使用 `/preview-access`、`/material-lab-access` 或接口列表返回的短时签名 URL。
3. C端作品要先通过 `/consumer-assets/{id}/submit-review` 与管理员审核，3D 模型通过后才可提交打样/批量生产。
4. 微信支付、退款、日账单对账接口必须配置商户私钥、平台证书/公钥、API v3 Key、回调地址等生产密钥；不能把客户端回调当作入账依据。
5. 物流同步仅在快递100 `customer/key` 配置后可真实查询；订阅回调还必须配置公网 `callback-url` 与 `salt`。
6. AI 生成结果、生产可行性评分、报价、交期、版权建议均是辅助信息，最终上线、下单、付款、生产、授权需要人工审核。
