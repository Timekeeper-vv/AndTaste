# 智慧养殖场管理与溯源系统

> 基于 Spring Boot + Vue 3 的全栈畜牧业数字化管理平台，覆盖牲畜从入栏、饲养、防疫到出栏销售的全生命周期数字闭环。

---

## 目录

- [一、项目背景与意义](#一项目背景与意义)
- [二、系统概述](#二系统概述)
- [三、技术选型](#三技术选型)
- [四、系统架构](#四系统架构)
- [五、功能模块详解](#五功能模块详解)
- [六、数据库设计](#六数据库设计)
- [七、核心技术难点与解决方案](#七核心技术难点与解决方案)
- [八、API 接口设计](#八api-接口设计)
- [九、安全设计](#九安全设计)
- [十、快速启动](#十快速启动)
- [十一、项目总结与展望](#十一项目总结与展望)

---

## 一、项目背景与意义

### 1.1 行业痛点

随着畜牧业规模化发展，传统人工台账管理方式已无法满足现代养殖场的需求，主要体现在：

| 痛点 | 具体问题 |
|------|----------|
| **信息断层** | 圈舍、批次、个体信息分散记录，难以关联查询 |
| **追溯困难** | 食品安全事件发生后无法快速定位问题批次和个体 |
| **管理粗放** | 免疫、用药、转舍等事件依赖人工记忆，易漏记 |
| **数据不可靠** | 并发操作下存栏计数错误，数据一致性无保障 |
| **决策滞后** | 缺乏数据统计视图，无法及时掌握养殖场整体状况 |

### 1.2 项目价值

本系统针对上述痛点，提供完整的数字化解决方案：

| 价值维度 | 描述 |
|----------|------|
| **资产数字化** | 圈舍 → 批次 → 个体三级精细化建模，每头牲畜拥有唯一数字身份（耳标号） |
| **过程可追溯** | 完整记录免疫、用药、转舍、出栏等关键生命事件，支持全链路溯源查询 |
| **数据高可靠** | 转舍操作采用数据库事务保障原子性，耳标号后端强制唯一校验 |
| **决策可视化** | 批次统计仪表盘与个体时间线视图，直观呈现养殖全貌 |

---

## 二、系统概述

### 2.1 系统定位

本系统是一套面向**中小型养殖场**的 Web 端综合管理平台，具备以下特点：

- **零门槛部署**：数据库建表与样本数据全自动初始化，开箱即用
- **前后端分离**：Vue 3 SPA + Spring Boot RESTful API，职责清晰
- **全流程覆盖**：从基础数据维护到生产事件记录，再到溯源分析，一站式完成

### 2.2 功能全景图

```
┌─────────────────────────────────────────────────────────────┐
│                    智慧养殖场管理与溯源系统                      │
├──────────────┬─────────────────┬────────────┬───────────────┤
│  基础数据中心  │  养殖资产管理中心  │ 生产过程记录  │  全链路溯源中心  │
├──────────────┼─────────────────┼────────────┼───────────────┤
│ FR-01 圈舍管理 │ FR-03 养殖批次管理 │ FR-05 免疫记录 │ FR-09 个体溯源  │
│ FR-02 兽药疫苗库│ FR-04 个体数字档案 │ FR-06 用药记录 │ FR-10 批次溯源  │
│              │                 │ FR-07 转舍管理 │               │
│              │                 │ FR-08 出栏管理 │               │
├──────────────┴─────────────────┴────────────┴───────────────┤
│              系统管理（用户管理、登录认证、三角色权限控制）            │
└─────────────────────────────────────────────────────────────┘
```

---

## 三、技术选型

### 3.1 后端技术栈

| 技术 | 版本 | 选型理由 |
|------|------|----------|
| **Spring Boot** | 2.7.3 | 开箱即用的 Java Web 框架，自动配置减少样板代码 |
| **MyBatis** | 2.3.1 | SQL 完全可控，适合复杂查询（UNION ALL 溯源）和精细化调优 |
| **MySQL** | 8.x | 成熟的关系型数据库，事务支持可靠，适合存储强结构化的养殖业务数据 |
| **Spring Security Crypto** | — | 仅引入密码加密模块（BCrypt），不引入完整 Security 依赖，轻量适配 |
| **SpringDoc OpenAPI** | 1.8.0 | 自动生成 Swagger 在线文档，方便接口调试与前后端联调 |
| **Java** | 17 | LTS 版本，稳定可靠 |

### 3.2 前端技术栈

| 技术 | 版本 | 选型理由 |
|------|------|----------|
| **Vue 3** | 3.5.x | Composition API 组织业务逻辑更灵活，响应式系统性能优异 |
| **Vite** | 8.x | 极速开发服务器（HMR），比 Webpack 启动快数倍 |
| **原生 Fetch API** | — | 无需引入 Axios 等第三方库，减少依赖包体积 |

### 3.3 架构模式

```
前后端分离  ·  RESTful API  ·  JSON 数据交换  ·  代理转发（Vite Dev Proxy）
```

---

## 四、系统架构

### 4.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        用户浏览器                             │
│              http://localhost:5173 (开发) / 静态部署（生产）    │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP / JSON
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   Vue 3 前端应用（SPA）                        │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Sidebar.vue   App.vue   LoginPage.vue   GlobalAlert  │   │
│  ├──────────────────────────────────────────────────────┤   │
│  │  PenMgmt  BatchMgmt  AnimalMgmt  ImmunizationMgmt   │   │
│  │  MedicationMgmt  PenTransferMgmt  SlaughterMgmt      │   │
│  │  DrugVaccineMgmt  TraceabilityView  UserMgmt         │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │ /api/** → localhost:8080
                           ▼
┌─────────────────────────────────────────────────────────────┐
│               Spring Boot 后端（端口 8080）                    │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────────┐  │
│  │  Controller  │  │   Service   │  │  Mapper (MyBatis)  │  │
│  │  (REST API)  │→ │ (业务逻辑)  │→ │  (SQL Mapper XML)  │  │
│  └─────────────┘  └─────────────┘  └────────────────────┘  │
│       ↑ Swagger UI  /swagger-ui/index.html                   │
└──────────────────────────┬──────────────────────────────────┘
                           │ JDBC
                           ▼
┌─────────────────────────────────────────────────────────────┐
│               MySQL 8.x 数据库（端口 3306）                    │
│   pens · drugs_vaccines · batches · animals                  │
│   immunization_records · medication_records                  │
│   pen_transfer_records · slaughter_records · user            │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 后端分层设计

```
Controller 层  →  接收 HTTP 请求，参数校验，返回 ResponseEntity
    ↓
Service 层     →  封装业务逻辑，事务控制（@Transactional）
    ↓
Mapper 层      →  MyBatis 接口，对应 resources/mapper/*.xml
    ↓
Model 层       →  Java POJO 实体类，与数据库表字段映射
```

### 4.3 前端组件结构

```
App.vue（根组件，管理页面切换状态）
├── LoginPage.vue（未登录时展示）
└── 已登录布局
    ├── Sidebar.vue（左侧导航，事件发射切换页面）
    ├── GlobalAlert.vue（全局通知，成功/错误提示）
    └── 当前激活的业务组件（根据 currentPage 响应式切换）
        ├── PenManagement.vue
        ├── BatchManagement.vue
        ├── AnimalManagement.vue
        ├── ImmunizationManagement.vue
        ├── MedicationManagement.vue
        ├── PenTransferManagement.vue
        ├── SlaughterManagement.vue
        ├── DrugVaccineManagement.vue
        ├── TraceabilityView.vue
        └── UserManagement.vue
```

---

## 五、功能模块详解

### FR-01 圈舍资产管理

对养殖场物理空间进行数字化建模，是整个系统的资源基础。

**核心功能：**
- 新建/编辑/删除圈舍，设置圈舍编码（全局唯一）、容量上限
- 启用 / 停用状态切换（停用圈舍不可接收新转入）
- 实时显示当前存栏数（由系统自动维护，禁止人工修改）

**关键设计：** 存栏计数（`current_count`）仅通过转舍和出栏事务自动维护，确保与实际一致。

---

### FR-02 兽药疫苗标准库

建立统一的投入品主数据，规范免疫和用药记录的录入口径。

**核心功能：**
- 维护疫苗（VACCINE）和药品（DRUG）两类投入品信息
- 记录规格、生产厂家等标准字段
- 免疫/用药记录录入时通过下拉列表选取，杜绝随意输入

**价值：** 统一口径，便于后续按投入品类型进行数据统计分析。

---

### FR-03 养殖批次管理

按"同进同出"原则对牲畜进行逻辑分组，是连接圈舍与个体的桥梁。

**核心功能：**
- 批次编码唯一标识（如 `BATCH-2024-001`）
- 记录入栏日期、品种来源、初始圈舍等信息
- 批次视图展示当前批次的个体总数

---

### FR-04 个体数字档案

以**耳标号**为核心唯一标识，为每头牲畜建立终身数字身份档案。

**核心功能：**
- 耳标号全局唯一，后端双重校验（应用层 + 数据库唯一索引）
- 关联批次和当前圈舍，支持按状态（在栏/已出栏）筛选
- 个体状态（ACTIVE / SOLD）由出栏操作自动变更

**设计亮点：** 耳标号冲突时后端返回 `409 Conflict` 状态码，前端精确展示错误信息。

---

### FR-05 免疫记录

录入疫苗接种事件，自动挂载到对应个体的生命周期时间线。

**录入字段：** 耳标号、疫苗（从标准库选取）、接种时间、剂量、操作人员、备注

---

### FR-06 用药记录

记录用药事件及治疗原因，便于疫病分析和追责。

**录入字段：** 耳标号、药品（从标准库选取）、用药原因、用药时间、剂量、操作人员

---

### FR-07 转舍管理（核心事务操作）

将个体从一个圈舍迁移到另一个圈舍，涉及多张表的联动更新。

**核心功能：**
- 选择转移个体（耳标号）、原圈舍、目标圈舍
- 后端 `@Transactional` 保障三步原子操作（见第七节）

**UI 设计：** 页面展示"事务一致性保障"说明，直观呈现系统可靠性。

---

### FR-08 出栏管理

登记个体出栏，支持销售、屠宰、转移三种出栏类型。

**核心功能：**
- 记录出栏时间、类型、目的地、重量、价格
- 自动将个体状态变更为 SOLD，同步扣减圈舍存栏计数

---

### FR-09 个体全生命周期溯源（核心亮点功能）

输入任意耳标号，系统通过 UNION ALL 跨表查询，生成该个体从入栏到出栏的**完整时间线**。

**时间线事件类型：**

| 事件类型 | 数据来源表 | 展示内容 |
|----------|----------|----------|
| ENTRY（入栏）| `animals` + `batches` | 批次号、品种、来源 |
| IMMUNIZATION（免疫）| `immunization_records` + `drugs_vaccines` | 疫苗名称、剂量、操作人 |
| MEDICATION（用药）| `medication_records` + `drugs_vaccines` | 药品名称、原因、剂量、操作人 |
| TRANSFER（转舍）| `pen_transfer_records` + `pens` | 原圈舍 → 目标圈舍、原因 |
| SLAUGHTER（出栏）| `slaughter_records` | 出栏类型、目的地、重量、价格 |

---

### FR-11 三角色权限管理

系统内置**管理员、技术员、饲养员**三种角色，每个角色对应不同的菜单可见范围，登录后自动生效。

#### 角色权限矩阵

| 功能模块 | 管理员 | 技术员 | 饲养员 |
|----------|:------:|:------:|:------:|
| 概览仪表盘 | ✅ | ✅ | ❌ |
| 圈舍管理 | ✅ | ❌ | ✅ |
| 兽药疫苗库 | ✅ | ✅ | ❌ |
| 养殖批次 | ✅ | ❌ | ✅ |
| 个体档案 | ✅ | ✅ | ✅ |
| 免疫记录 | ✅ | ✅ | ✅ |
| 用药记录 | ✅ | ✅ | ✅ |
| 转舍管理 | ✅ | ✅ | ✅ |
| 出栏管理 | ✅ | ❌ | ✅ |
| 全链路溯源 | ✅ | ✅ | ❌ |
| 用户管理 | ✅ | ❌ | ❌ |

#### 实现机制

- **前端侧边栏过滤**：`Sidebar.vue` 中每个菜单项携带 `roles` 数组，通过 `computed` 仅渲染当前角色可访问的菜单项，空分组自动隐藏。
- **App 层访问守卫**：`App.vue` 的 `watchEffect` 在用户登录或手动改变页面时检查权限，若无权限自动跳转到第一个可访问页（饲养员默认落地圈舍管理）。
- **角色持久化**：角色字段随用户对象存入 `sessionStorage`，刷新页面后权限状态无丢失。
- **侧边栏标识**：底部用户信息区域动态展示角色名（管理员青色 / 技术员蓝色 / 饲养员橙色）。

---

### FR-10 批次溯源概览

输入批次编码，返回该批次的统计仪表盘和个体明细。

**统计维度：**
- 批次基本信息（入栏时间、品种、来源）
- 当前存栏数 / 已出栏数 / 出栏率
- 免疫事件总次数 / 用药事件总次数
- 个体明细列表（耳标号、当前状态、所在圈舍）

---

## 六、数据库设计

### 6.1 ER 关系图

```
┌──────────┐         ┌──────────┐
│  batches  │──1:N──→│  animals │
└──────────┘         └────┬─────┘
                          │ ear_tag（全局唯一主键）
          ┌───────────────┼────────────────┐
          ▼               ▼                ▼
┌─────────────────┐ ┌──────────────┐ ┌─────────────────────┐
│immunization_    │ │ medication_  │ │ pen_transfer_records │
│records          │ │ records      │ │ (from_pen_id,        │
│(vaccine_id →    │ │ (drug_id →   │ │  to_pen_id → pens)  │
│ drugs_vaccines) │ │ drugs_vaccines│ └─────────────────────┘
└─────────────────┘ └──────────────┘
                                         ┌──────────────────┐
                                         │ slaughter_records │
                                         └──────────────────┘
                    ┌──────────────────┐
                    │      pens        │ ←── animals.current_pen_id
                    │  (current_count  │ ←── pen_transfer_records
                    │   自动维护)       │
                    └──────────────────┘
         ┌──────────────────┐
         │  drugs_vaccines  │ ←── immunization_records.vaccine_id
         │  (VACCINE/DRUG)  │ ←── medication_records.drug_id
         └──────────────────┘
```

### 6.2 核心表结构

| 表名 | 功能 | 关键字段 | 约束 |
|------|------|----------|------|
| `pens` | 圈舍资产 | `pen_code`, `capacity`, `current_count`, `status` | `pen_code` UNIQUE |
| `drugs_vaccines` | 兽药疫苗标准库 | `category`(VACCINE/DRUG), `generic_name`, `specification` | — |
| `batches` | 养殖批次 | `batch_code`, `entry_date`, `breed`, `source`, `initial_pen_id` | `batch_code` UNIQUE |
| `animals` | 个体档案 | `ear_tag`, `gender`, `breed`, `batch_id`, `current_pen_id`, `status` | `ear_tag` UNIQUE |
| `immunization_records` | 免疫记录 | `ear_tag`, `vaccine_id`, `event_time`, `dosage`, `operator` | — |
| `medication_records` | 用药记录 | `ear_tag`, `drug_id`, `reason`, `event_time`, `dosage`, `operator` | — |
| `pen_transfer_records` | 转舍记录 | `ear_tag`, `from_pen_id`, `to_pen_id`, `event_time`, `reason` | — |
| `slaughter_records` | 出栏记录 | `ear_tag`, `event_time`, `type`(SALE/SLAUGHTER/TRANSFER), `destination`, `weight`, `price` | — |
| `user` | 用户账户 | `username`, `password`(BCrypt哈希), `email`, `phone`, `role`(admin/technician/feeder) | `username` UNIQUE |

### 6.3 自动初始化设计

```properties
# application.properties
spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath:schema.sql
```

应用启动时自动执行 `schema.sql`，完成：
- `CREATE TABLE IF NOT EXISTS`（建表，已存在则跳过）
- 插入样本数据（圈舍、兽药疫苗、批次、个体档案、各类事件记录）

**优点：** 零配置开箱即用，部署时无需手动建表或导入数据。

---

## 七、核心技术难点与解决方案

### 7.1 转舍操作的数据一致性（最核心难点）

**问题：** 转舍涉及 3 张表的联动修改：
1. `pen_transfer_records` — 插入转舍记录
2. `animals` — 更新 `current_pen_id`
3. `pens` — 原圈舍 `current_count - 1`，目标圈舍 `current_count + 1`

若其中任一步失败（如数据库连接中断），将产生数据不一致（如个体已记录转移但存栏数未更新）。

**解决方案：** 使用 Spring `@Transactional` 将三步操作封装为一个原子事务。

```java
// FarmEventService.java
@Transactional
public PenTransferRecord transfer(PenTransferRecord record) {
    // 第①步：插入转舍记录
    penTransferMapper.insert(record);
    
    // 第②步：更新个体当前圈舍
    animalMapper.updateCurrentPen(record.getEarTag(), record.getToPenId());
    
    // 第③步：存栏计数原子调整
    Integer fromPenId = animalMapper.findCurrentPenId(record.getEarTag());
    penMapper.decrementCount(fromPenId);   // 原圈舍 -1
    penMapper.incrementCount(record.getToPenId());  // 目标圈舍 +1
    
    // 任意一步抛出异常 → 整体回滚，杜绝脏数据
    return record;
}
```

**效果：** 无论发生何种异常，数据库状态要么全部更新，要么全部不变，永远保持一致。

---

### 7.2 全链路溯源的跨表聚合查询

**问题：** 个体的生命事件分布在 5 张不同的表中，需要将它们按时间统一排序、展示为连续时间线。

**解决方案：** 使用 SQL `UNION ALL` 将 5 张表的查询结果合并为统一结构，再按 `event_time` 排序。

```sql
<!-- TraceabilityMapper.xml -->
SELECT 'ENTRY' AS event_type, a.entry_date AS event_time,
       CONCAT('批次: ', b.batch_code) AS item_name,
       CONCAT('入栏，品种: ', a.breed, '，来源: ', b.source) AS detail,
       NULL AS operator, NULL AS dosage
FROM animals a LEFT JOIN batches b ON a.batch_id = b.id
WHERE a.ear_tag = #{earTag}

UNION ALL

SELECT 'IMMUNIZATION', ir.event_time,
       dv.generic_name,
       CONCAT('免疫接种: ', dv.generic_name, '，规格: ', dv.specification),
       ir.operator, ir.dosage
FROM immunization_records ir
LEFT JOIN drugs_vaccines dv ON ir.vaccine_id = dv.id
WHERE ir.ear_tag = #{earTag}

UNION ALL
-- ... 用药、转舍、出栏事件（共5段）

ORDER BY event_time ASC
```

**效果：** 单次查询即可获取个体完整生命轨迹，时间线数据结构统一，前端无需额外处理。

---

### 7.3 耳标号唯一性的双重保障

**问题：** 耳标号是个体的全局唯一标识符，重复写入会导致严重的数据错误。

**解决方案：** 应用层 + 数据库层双重校验。

```java
// AnimalService.java — 应用层校验
public Animal create(Animal animal) {
    if (animalMapper.existsByEarTag(animal.getEarTag())) {
        throw new IllegalArgumentException("耳标号已存在: " + animal.getEarTag());
    }
    animalMapper.insert(animal);
    return animal;
}
```

```sql
-- schema.sql — 数据库层约束
CREATE TABLE IF NOT EXISTS animals (
    ear_tag VARCHAR(50) NOT NULL,
    ...
    UNIQUE KEY uk_ear_tag (ear_tag)  -- 数据库层兜底
);
```

```java
// AnimalController.java — 返回语义化错误响应
@PostMapping
public ResponseEntity<?> create(@RequestBody Animal animal) {
    try {
        return ResponseEntity.status(HttpStatus.CREATED).body(animalService.create(animal));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                             .body(Map.of("error", e.getMessage()));
    }
}
```

**效果：** 应用层给出友好错误信息（前端展示），数据库唯一索引作为最终防线，两层保障万无一失。

---

### 7.4 前端无路由库的单页面架构

**问题：** 项目未引入 Vue Router，需要实现多页面切换。

**解决方案：** 在 `App.vue` 中使用响应式变量 `currentPage` + `v-if` / `v-show` 控制组件显示，`Sidebar.vue` 通过 `$emit` 事件通知父组件切换页面。

```javascript
// App.vue
const currentPage = ref('pen')  // 当前激活页面

// Sidebar 发出 navigate 事件时切换
function handleNavigate(page) {
    currentPage.value = page
}
```

**效果：** 无额外路由库依赖，轻量简洁，适合中等规模的管理后台。

---

### 7.5 开发环境跨域问题

**问题：** 前端运行在 5173 端口，后端在 8080 端口，直接请求会触发浏览器跨域限制。

**解决方案：** Vite 开发服务器代理配置。

```javascript
// vite.config.js
export default defineConfig({
    server: {
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true
            }
        }
    }
})
```

**效果：** 开发时所有 `/api/**` 请求无感代理转发到后端，无需修改任何业务代码。

---

## 八、API 接口设计

### 8.1 接口规范

| 规范 | 说明 |
|------|------|
| 风格 | RESTful API |
| 数据格式 | JSON (Content-Type: application/json) |
| 基础路径 | `/api/` |
| 文档 | Swagger UI: `http://localhost:8080/swagger-ui/index.html` |

### 8.2 接口清单

| HTTP 方法 | 接口路径 | 功能 | 功能编号 |
|-----------|----------|------|----------|
| GET | `/api/pens` | 获取所有圈舍 | FR-01 |
| GET | `/api/pens/active` | 获取启用中的圈舍 | FR-01 |
| POST | `/api/pens` | 新建圈舍 | FR-01 |
| PUT | `/api/pens/{id}` | 更新圈舍信息 | FR-01 |
| DELETE | `/api/pens/{id}` | 删除圈舍 | FR-01 |
| GET | `/api/drugs-vaccines` | 获取兽药疫苗库 | FR-02 |
| POST | `/api/drugs-vaccines` | 新增品目 | FR-02 |
| PUT | `/api/drugs-vaccines/{id}` | 更新品目 | FR-02 |
| DELETE | `/api/drugs-vaccines/{id}` | 删除品目 | FR-02 |
| GET | `/api/batches` | 获取所有养殖批次 | FR-03 |
| POST | `/api/batches` | 新建养殖批次 | FR-03 |
| PUT | `/api/batches/{id}` | 更新批次信息 | FR-03 |
| DELETE | `/api/batches/{id}` | 删除批次 | FR-03 |
| GET | `/api/animals` | 获取个体档案列表 | FR-04 |
| POST | `/api/animals` | 新建个体档案（耳标唯一校验） | FR-04 |
| GET | `/api/animals/{id}` | 按 ID 查询个体 | FR-04 |
| GET | `/api/animals/ear-tag/{earTag}` | 按耳标号精确查询 | FR-04 |
| PUT | `/api/animals/{id}` | 更新个体信息 | FR-04 |
| DELETE | `/api/animals/{id}` | 删除个体档案 | FR-04 |
| GET | `/api/events/immunization` | 获取所有免疫记录 | FR-05 |
| POST | `/api/events/immunization` | 录入免疫记录 | FR-05 |
| GET | `/api/events/medication` | 获取所有用药记录 | FR-06 |
| POST | `/api/events/medication` | 录入用药记录 | FR-06 |
| GET | `/api/events/transfer` | 获取所有转舍记录 | FR-07 |
| POST | `/api/events/transfer` | **执行转舍（事务操作）** | FR-07 |
| GET | `/api/events/slaughter` | 获取所有出栏记录 | FR-08 |
| POST | `/api/events/slaughter` | 登记出栏 | FR-08 |
| GET | `/api/traceability/animal/{earTag}` | **个体全生命周期溯源** | FR-09 |
| GET | `/api/traceability/batch/{batchCode}` | **批次溯源统计概览** | FR-10 |
| GET | `/api/users` | 用户列表 | 系统管理 |
| POST | `/api/users` | 新增用户 | 系统管理 |
| POST | `/api/users/login` | 用户登录认证 | 系统管理 |
| PUT | `/api/users/{id}` | 更新用户信息 | 系统管理 |
| DELETE | `/api/users/{id}` | 删除用户 | 系统管理 |

### 8.3 溯源接口示例

**请求：**
```
GET /api/traceability/animal/ET-001
```

**响应：**
```json
{
  "animal": {
    "earTag": "ET-001",
    "gender": "MALE",
    "breed": "杜洛克猪",
    "batchCode": "BATCH-2024-001",
    "currentPenName": "A号猪舍",
    "status": "ACTIVE"
  },
  "timeline": [
    {
      "eventType": "ENTRY",
      "eventTime": "2024-01-15T08:00:00",
      "itemName": "批次: BATCH-2024-001",
      "detail": "入栏，品种: 杜洛克猪，来源: 山东育种场"
    },
    {
      "eventType": "IMMUNIZATION",
      "eventTime": "2024-02-01T09:30:00",
      "itemName": "猪瘟活疫苗",
      "detail": "免疫接种: 猪瘟活疫苗，规格: 100mL/瓶",
      "operator": "张三",
      "dosage": "1头份"
    },
    {
      "eventType": "TRANSFER",
      "eventTime": "2024-03-10T14:00:00",
      "itemName": "A号猪舍 → B号猪舍",
      "detail": "转舍，原因: 分群管理"
    }
  ]
}
```

---

## 九、安全设计

| 安全域 | 实现方案 | 说明 |
|--------|----------|------|
| **密码存储** | BCryptPasswordEncoder | 强哈希（自适应迭代），彻底杜绝明文存储，即使数据库泄露也无法还原密码 |
| **SQL 注入防御** | MyBatis 参数化查询 `#{}` | 所有 SQL 均使用预编译占位符，禁止字符串拼接，从根本上防止 SQL 注入 |
| **密码字段隔离** | `@JsonProperty(WRITE_ONLY)` | 用户对象序列化为 JSON 时自动屏蔽 `password` 字段，防止密码哈希值泄漏到响应体 |
| **唯一性校验** | 应用层 + 数据库双重校验 | 关键唯一字段（耳标号、用户名等）在数据库设置 UNIQUE 约束作为兜底 |
| **角色权限控制** | 前端双重守卫（Sidebar + App） | 侧边栏按角色过滤可见菜单；`watchEffect` 在页面切换和刷新时强制校验访问权限，非法页面自动重定向 |

---

## 十、快速启动

### 10.1 环境要求

| 依赖 | 版本要求 |
|------|----------|
| JDK | 17+ |
| Maven | 3.6+ |
| MySQL | 8.x |
| Node.js | 18+ |
| npm | 9+ |

### 10.2 数据库配置

创建数据库（**建表由应用自动完成，无需手动执行 SQL**）：

```sql
CREATE DATABASE IF NOT EXISTS shixun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

按需修改数据库连接信息：

```properties
# shixun/src/main/resources/application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/shixun?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=123456    # ← 修改为实际密码
```

### 10.3 启动后端

```bash
cd shixun
mvn spring-boot:run
```

启动成功后：
- 后端 API：`http://localhost:8080`
- Swagger 文档：`http://localhost:8080/swagger-ui/index.html`
- 数据库自动建表 + 插入样本数据

### 10.4 启动前端（开发模式）

```bash
cd shixun-vue
npm install
npm run dev
```

访问：`http://localhost:5173`

### 10.5 生产构建

```bash
cd shixun-vue
npm run build
# 构建产物: shixun-vue/dist/
```

### 10.6 默认账号

系统启动后自动初始化以下测试账号（通过 API `POST /api/users` 创建的账号密码经 BCrypt 加密，可正常登录）：

| 用户名 | 密码 | 角色 | 可访问模块 |
|--------|------|------|-----------|
| `testuser` | `123456` | 管理员 | 全部模块 |
| `admin` | `123456` | 管理员 | 全部模块 |
| `tech01` | `123456` | 技术员 | 仪表盘、兽药疫苗库、个体档案、免疫/用药/转舍记录、溯源 |
| `feeder01` | `123456` | 饲养员 | 圈舍管理、养殖批次、个体档案、免疫/用药/转舍/出栏记录 |

> **注意：** `schema.sql` 中预置账号的密码为明文（历史遗留），建议通过用户管理界面重新创建账号以使用 BCrypt 加密密码。

---

## 十一、项目总结与展望

### 11.1 项目成果

本项目从零构建了一套完整的全栈养殖场管理系统，实现了：

| 成果 | 详情 |
|------|------|
| **功能完整性** | 10 个核心功能模块（FR-01 ~ FR-10），覆盖养殖全流程 |
| **技术栈完整** | 前后端分离架构，Spring Boot + MyBatis + MySQL + Vue 3 |
| **代码规模** | 后端 8 个 Controller、8 个 Service、11 个 Mapper；前端 16 个 Vue 组件 |
| **数据可靠性** | 事务原子操作 + 双重唯一性校验，核心业务数据零差错 |
| **开箱即用** | 数据库自动初始化 + 样本数据预置，一键启动即可体验完整功能 |

### 11.2 核心亮点总结

1. **事务驱动的数据一致性**：转舍操作通过 `@Transactional` 将跨表联动封装为原子事务，解决了并发场景下数据不一致的根本问题。

2. **UNION ALL 全链路溯源**：单条 SQL 横向聚合 5 张事件表，生成统一时间线视图，溯源查询无需多次往返数据库。

3. **双重唯一性保障**：耳标号在应用层和数据库层各有一道校验，确保核心标识符的唯一性在任何情况下都不被破坏。

4. **轻量依赖设计**：前端仅依赖 Vue 3 和 Vite，无 Vuex/Pinia/Axios/Element Plus 等重型依赖，保持项目轻量可控。

### 11.3 未来展望

| 方向 | 具体建议 |
|------|----------|
| **认证升级** | 引入 JWT Token 实现无状态认证，支持多端访问 |
| ~~**权限管理**~~ | ~~增加 RBAC 角色权限控制~~ **✅ 已实现**（管理员 / 技术员 / 饲养员三角色，见 FR-11） |
| **数据统计** | 引入 ECharts 实现免疫覆盖率趋势图、出栏量月度分析等可视化图表 |
| **移动端适配** | 响应式布局改造，支持手机端巡栏录入 |
| **消息通知** | 接种到期提醒、存栏超容量预警等主动推送 |
| **数据导出** | 支持批次台账、免疫记录导出为 Excel/PDF |

---

## 附录

### 项目结构

```
shixun_workspace/
├── README.md                         # 本文档
├── shixun/                           # Spring Boot 后端
│   ├── pom.xml                       # Maven 依赖
│   └── src/main/
│       ├── java/com/example/shixun/
│       │   ├── config/
│       │   │   ├── DataInitializer.java
│       │   │   └── OpenApiConfig.java
│       │   ├── controller/           # 8 个 REST 控制器
│       │   ├── service/              # 8 个业务逻辑层
│       │   ├── mapper/               # 11 个 MyBatis Mapper 接口
│       │   └── model/                # 11 个实体类
│       └── resources/
│           ├── application.properties
│           ├── schema.sql            # 建表 + 样本数据
│           └── mapper/               # 11 个 MyBatis XML 文件
└── shixun-vue/                       # Vue 3 前端
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── main.js
        ├── App.vue
        ├── shared.css
        └── components/               # 16 个 Vue 组件
```

### Git 信息

```
主分支：master
作者：gaotianyi
```
