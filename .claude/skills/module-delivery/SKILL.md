---
name: module-delivery
description: Hify 业务模块的端到端交付流程（设计咨询 → 数据模型 → 后端分层 → 逐步验证 → 前端对接 → 完整验收），含每步产出物、验证方式、必须等待用户确认的决策点和项目踩坑清单。当用户要求开发、交付或实现一个业务模块/功能（provider、agent、chat、mcp、workflow、knowledge 及其子功能），或继续推进某个未完成模块的下一步时使用。
---

# Hify 业务模块交付流程

按六个步骤顺序执行。**每步的产出物完成且验证通过后，才进入下一步**；步骤 1-2 是关键决策点，必须等待用户确认。

## 流程总览

| 步骤 | 产出物 | 验证方式 |
|---|---|---|
| 1 设计咨询 | 决策记录（选型对比、数据模型草案、边界结论） | **等待用户确认**（拍板后才动代码） |
| 2 数据模型 | schema.sql 更新 + 关联方同步清单 | SQL 可执行；表结构与实体/DTO/前端类型逐字段核对 |
| 3 后端分层 | Entity+Mapper → Req/Resp DTO → Service(接口+Impl) → Controller | 每层 `mvn -q -DskipTests package` 通过后再进下一层 |
| 4 逐步验证 | 可调用面的 curl 请求/响应样例 | curl 实测通过（成功 + 异常路径）再进下一步 |
| 5 前端对接 | api/<module>.ts 类型化方法 + 页面替换 mock | `npm run build`（vue-tsc 严格类型检查）通过 |
| 6 完整验收 | 验收记录 | 后端 curl 全接口 + 浏览器全流程 |

## 各步骤详解

### 1. 设计咨询（等待用户确认）

- 先盘点现状（已有表/实体/服务/前端页面），再提方案
- 存在分歧或多种可行路径时（技术选型、鉴权存储结构、缓存策略、字段设计、放置模块位置），用 AskUserQuestion 给出 2-3 个方案对比，让用户拍板
- 产出：被采纳方案 + 理由 + 被否决方案的取舍说明
- **未确认前不写业务代码**；用户给出的设计稿（如 DDL）以用户版本为准

### 2. 数据模型（等待用户确认）

- 以用户确认的 DDL 为准；发现遗漏（如注释声明"唯一"却没有 UNIQUE 约束）要补上并说明理由
- **同步清单——漏一处就运行时报错**：schema.sql、Entity 字段与注解、DTO、前端 api 类型字典、注释/校验里的枚举值（新增供应商类型要改 5 处：schema 注释、两个 @Pattern、后端分发 switch、前端 PROVIDER_TYPES）
- 破坏性变更（DROP 重建、字段改名）必须说明影响面并等用户确认
- 验证：本地库重建后 `SHOW COLUMNS` 或直接起后端调用验证

### 3. 后端分层（顺序不可跳）

```
Entity + Mapper → Req/Resp DTO → Service 接口 + Impl → Controller
```

- Entity 继承 BaseEntity（id/时间自动填充/逻辑删除）；无 created_at/deleted 的表（如 provider_health）用独立实体并注释偏离原因
- 请求 DTO 用 `@NotBlank/@Pattern/@Size` 把非法值拦在入口；响应 DTO 负责脱敏
- Service Impl 手写业务（不依赖 MP 的 IService），构造注入 Mapper
- 每层完成后：`mvn -q -DskipTests package`（**全 reactor**，别只编单模块——跨模块引用会漏编译）
- 涉及缓存的读写方法：读 `@Cacheable`、写 `@CacheEvict`，新类型/新数据源同步检查缓存序列化

### 4. 逐步验证

- 每完成一个可调用面就 curl 实测再继续：成功路径 + 必填缺失 + 不存在的 id，至少三条
- 需要本地库的验证，先确认 schema 已重建到最新（`IF NOT EXISTS` 不会更新已存在的表）

### 5. 前端对接

- 先在 `src/api/<module>.ts` 建类型 + 方法（类型对齐后端 DTO，含枚举字面量联合类型）
- 页面把 mock 换成真实调用：`HifyTable :api`、`HifyFormDialog @submit`、`useConfirm` 包删除
- 错误提示交给 axios 拦截器统一处理，页面只处理"后续动作"（关闭弹窗、刷新列表、保持弹窗调 setSubmitting(false)）

### 6. 完整验收

- 后端：6 类接口 curl 全过（创建/列表/详情/更新/删除/动词接口）
- 前端：浏览器全流程（新增 → 列表看到 → 编辑 → 测试 → 删除），含失败路径（必填漏填提示、删除确认取消）

## 关键决策点（必须等待用户确认）

1. 步骤 1 的技术选型与方案对比
2. 数据模型的字段增删、破坏性变更
3. 接口契约变更（路径、响应结构、校验规则、状态码语义）
4. 引入技术栈以外的新依赖（先问再引）
5. 缓存/并发/事务等横切策略的取舍
6. 演示性代码的放置位置与生命周期（如 mock 页面、demo 实体）

## 注意事项（项目踩坑实录）

### 构建与依赖

- **新业务模块 pom 必须自己声明 lombok**（provided 作用域不传递）；Lombok 对 enum 不生成构造器和 getter，枚举手写
- 编译"通过"但 getter 缺失 → Lombok 注解处理没跑：maven-compiler-plugin 3.13+/JDK 23+ 需显式 `annotationProcessorPaths`（根 pom 已配），用 `javap` 验证生成的 class
- MyBatis-Plus 3.5.9+：分页拦截器拆到 `mybatis-plus-jsqlparser`；`IService/ServiceImpl` 已迁移到新 artifact——不要用，直接 BaseMapper + 手写 Service 层
- OkHttp 5.x 的 `okhttp` 是 KMP 元数据空壳 jar（0 个 class），Maven 必须依赖 **`okhttp-jvm`**
- 任何新 artifact 先加根 pom `dependencyManagement` 管版本，子模块引用不写 version

### MyBatis-Plus

- `@MapperScan` 必须带 `markerInterface = BaseMapper.class`：否则 service 接口会被注册成 Mapper bean，注入后报 `Invalid bound statement (not found): xxxService.method`
- JSON 列（auth_config、extra_params）必须 `@TableField(typeHandler = JacksonTypeHandler.class)` **加** `@TableName(autoResultMap = true)`——只有前者时 insert/update 正常但 select 读不出
- 逻辑删除 + 唯一索引冲突：唯一约束对已删行仍生效，重名校验走应用层（MP 查询自动过滤 deleted），DB 层冲突异常要兜底转换
- 实体字段增删必须同步 schema.sql 且本地库 ALTER/DROP 重建，否则 `Unknown column 'xxx' in 'field list'`

### 缓存（Redis）

- `GenericJackson2JsonRedisSerializer` 默认构造内部 ObjectMapper **没有 JavaTimeModule**——缓存任何含 LocalDateTime 的对象直接 500（栈底是 `...RedisSerializer.serialize` 时就是这个坑）；统一用 `RedisConfig.redisJsonSerializer()`，两处配置共享
- 高频变化的数据（健康状态等）**不进缓存**：读接口带实时字段时不加 `@Cacheable`
- 写操作 `@CacheEvict(allEntries = true)`（分页列表 key 组合无法精确失效）；**新增（create）也要 evict**，否则列表返回缺新行的旧缓存

### 数据模型

- 先定"一行代表什么"再定字段：provider 行 = 一套连接 + 一套凭证（多实例同类型很常见），不是类型字典
- 高频写的状态数据独立成表（如 provider_health），避免污染主表、破坏主表缓存
- `schema.sql` 用 `IF NOT EXISTS`，改表后必须 DROP 重建或 ALTER——推荐首次重建时清点全部受影响表，按依赖顺序 DROP

### 安全与契约

- **凭证只写不读**：响应 DTO 永远不含 api_key/auth_config；编辑不回填密钥，更新时"空值 = 保持原值"写进 DTO 注释
- 枚举类字段用 `@Pattern` 白名单把非法值拦在入口，错误消息列出全部合法值
- 列表接口需要关联数据（健康状态、模型数）时，在 Service 层做**批量 IN 查询 + 内存聚合**，不要 N+1 逐行查

### 前端

- 泛型 SFC 不能 `InstanceType<typeof X<T>>`：用结构化最小接口声明 ref（`type TableApi = { refresh: () => Promise<void> }`）
- 泛型组件的事件参数会回退到泛型约束类型（`Record<string, any>`）：事件处理器接收后窄化断言
- 表格 slot 传出的 row 无类型：模板里用带兜底的辅助函数（如 `healthOf(status)`），避免 TS7053 索引报错
- 表格列全部保留、不隐藏：列用 `min-width` 保证弹性与可读，放不下时用横向滚动兜底
- 新增枚举值/字段时同步前端 api 模块的类型联合与字典数组

### 验证纪律

- 每步必编译：后端 `mvn -q -DskipTests package`（全 reactor）；前端改动跑 `npm run build`（vue-tsc 严格模式）
- curl 验证覆盖三类：成功、必填缺失（400 参数校验）、不存在的 id（NOT_FOUND）
- 定时任务/异步逻辑验证看日志关键字（如 `health-check:`），不要只信编译通过
