---
name: provider-adapter
description: Hify 新增/修改 LLM 供应商协议支持的固定流程（分析 API → 实现 Adapter → 注册到 Factory → 验证），含策略模式的真实代码结构、类型字典同步清单和验证命令。当用户要求接入新的模型供应商（如 DeepSeek、Kimi、Qwen、OpenRouter、vLLM 等）、修改某供应商的连通性测试/请求协议，或问"怎么加一个供应商类型"时使用。
---

# 新增供应商 Adapter 流程

Hify 的供应商协议差异由**策略模式**承载：每种协议一个 `ProviderAdapter`，`ProviderAdapterFactory` 自动收集分发。**禁止在 Service 里写 type 的 switch/if-else 分发**（重构前的老写法，`ProviderServiceImpl.testConnection` 现在是单行委托）。

## 真实代码结构（hify-provider）

```
com.hify.provider.adapter/
├── ProviderAdapter.java            # 接口: supportedTypes() + listModels(Provider) + testConnection(Provider)
├── AbstractProviderAdapter.java    # 抽象基类: HTTP/解析/鉴权/URL 辅助 + testConnection 模板实现
├── OpenAiAdapter.java              # OPENAI（Bearer + /v1/models + data[].id）
├── OpenAiCompatibleAdapter.java    # extends OpenAiAdapter，零额外代码，只换 supportedTypes
├── AnthropicAdapter.java           # ANTHROPIC（x-api-key + anthropic-version）
├── OllamaAdapter.java              # OLLAMA（/api/tags，无鉴权，models[].name）
└── ProviderAdapterFactory.java     # 构造注入 List<ProviderAdapter> 自动索引
```

调用链：`ProviderController.testConnection` → `ProviderServiceImpl.testConnection`（捕获 `LlmApiException` 转 `success=false`；`BizException` 直接抛）→ `ProviderAdapterFactory.getAdapter(type)` → 对应 Adapter。

**testConnection 是基类的模板方法**（`listModels()` 计时 + 计数），子类只实现 `listModels`。

## 固定流程

### Step 1 分析 API（决定要不要写代码）

先回答三个问题，**能复用就不写新 Adapter**：

| 问题 | 判断 |
|---|---|
| 鉴权方式 | `Authorization: Bearer` + `GET {base}/v1/models` + 响应 `data[]` → **OpenAI 兼容家族**，只加枚举值，零协议代码 |
| 端点差异 | 非 `/v1/models`（如 Ollama 的 `/api/tags`）→ 需要新 Adapter 的 URL |
| 响应结构差异 | 模型数组字段不是 `data`（如 `models`）→ 新 Adapter 换 `parseModelList` 的字段名参数 |

产出：一份 API 事实清单（端点、鉴权 header、响应字段、特殊 header 要求）。**若与现有 Adapter 都不同，说明差异点后再动手。**

### Step 2 实现 Adapter

```java
@Component
public class XxxAdapter extends AbstractProviderAdapter {

  private static final Set<String> TYPES = Set.of("XXX");

  public XxxAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
    super(llmHttpClient, objectMapper);
  }

  @Override
  public Set<String> supportedTypes() { return TYPES; }

  @Override
  public List<String> listModels(Provider provider) {
    String body = llmHttpClient.get(
        /* URL：v1ModelsUrl(baseUrl) 或 joinUrl(baseUrl, "/path") */,
        /* headers：bearerHeaders(provider) / x-api-key Map / 空 Map */,
        PROBE_TIMEOUT);                       // 基类常量，10s（CLAUDE.md）
    return parseModelIds(body, /* "data" | "models" */, /* "id" | "name" */);
  }
}
```

**OpenAI 兼容的供应商不要再写新类**：`OpenAiCompatibleAdapter extends OpenAiAdapter` 只覆盖 `supportedTypes()` 即可（DeepSeek 就是这样接入的零代码复用）。只有协议真正不同（端点/鉴权/响应字段）才写新 Adapter。

基类已提供、**不要重复实现**：`testConnection`（模板方法：listModels + 计时计数）、`bearerHeaders`（Bearer 鉴权）、`requireApiKey`（从 auth_config 取 apiKey，缺失抛 BizException）、`v1ModelsUrl`（容错 `/v1` 结尾）、`joinUrl`、`parseModelIds`（含"非法 JSON/缺字段"分类）。

错误边界（必须保持）：
- 传输/HTTP 失败 → 抛 `LlmApiException`，由 Service 转成 `success=false` 的探测结果
- 配置错误（缺 apiKey/base_url/未知 type）→ 抛 `BizException`，**不转成功或失败结果**，让调用方知道是配置问题

### Step 3 注册（自动 + 同步清单）

**注册本身零工作**：`ProviderAdapterFactory` 构造注入 `List<ProviderAdapter>`，按 `supportedTypes()` 建立索引；新 Adapter 加了 `@Component` 即生效。

**但类型字典必须同步 6 处——漏一处就报错**（DeepSeek 接入时实际改过的）：

| # | 位置 | 漏掉的症状 |
|---|---|---|
| 1 | 新 Adapter 的 `supportedTypes()` | Factory 抛"不支持的供应商类型" |
| 2 | `ProviderCreateReq` 的 `@Pattern` | 创建接口报参数校验错误 |
| 3 | `ProviderUpdateReq` 的 `@Pattern` | 更新接口报参数校验错误 |
| 4 | `schema.sql` 的 type 列注释 | 无运行影响，但文档失真 |
| 5 | 前端 `api/provider.ts` 的 `ProviderType` 联合类型 | vue-tsc 编译错 |
| 6 | 前端 `PROVIDER_TYPES` 数组 | 下拉框没有新选项 |

### Step 4 验证（缺一不可）

```bash
# 1) 全 reactor 编译（别只编单模块）
mvn -B -q -DskipTests package

# 2) 前端类型检查
cd hify-web && npm run build

# 3) 真机验证（后端已起）
curl -X POST http://localhost:8080/api/v1/providers/{id}/test-connection
#    期望: {"code":200,...,"data":{"success":true,"latencyMs":...,"modelCount":>0}}

# 4) 异常路径
curl -X POST http://localhost:8080/api/v1/providers/999/test-connection   # code 1002 NOT_FOUND
```

**`modelCount > 0` 才是真通过**（证明鉴权和端点都对）；`success=true` 但 `modelCount=0` 或 `success=false` 都要看 `errorMessage` 定位——它是分类后的原因（AUTH_FAILED/TIMEOUT/响应缺少模型列表字段等），不含凭证。

## 注意事项（踩坑实录）

- **优先走 OpenAI 兼容分支**：DeepSeek 的接入是零协议代码——分析后发现 Bearer + `/v1/models` + `data[]` 全同构，只加了枚举值。多数国产模型（Kimi/Qwen/GLM）同理，先验证这一点再考虑写 Adapter
- **不要用 if-else 分发**：新增供应商不是往 Service 加 case，是加 Adapter 类。Service 对协议无感知（这是重构的目的）
- **测试超时用基类的 `PROBE_TIMEOUT`**（10s，CLAUDE.md 的连通性测试约定），不要自己 new Duration
- **Anthropic 必须有 `anthropic-version` header**（当前固定 `2023-06-01`），缺了会被拒
- **凭证只写不读**：Adapter 从 `provider.getAuthConfig()` 这个 Map 里取 `apiKey`；响应 DTO 永不包含它
- **base_url 拼接要容错**：`v1ModelsUrl` 兼容带不带 `/v1` 两种填法，新协议自己拼 URL 时也用 `joinUrl` + `trimSlash`
- **改完 Adapter 先编译全 reactor**：单模块编译会漏掉跨模块引用变化
