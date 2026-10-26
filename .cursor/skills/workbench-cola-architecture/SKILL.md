---
name: workbench-cola-architecture
description: >-
  Guides Workbench Java backend design and code placement using COLA-inspired
  clean layered architecture mapped onto workbench-* Maven modules (Facade,
  Controller, Orchestrator, Job, infra). Use when adding features, choosing
  modules, refactoring layers, reviewing architecture, or when the user
  mentions COLA, 分层, Facade, 领域模块, or where to put code.
---

# Workbench × COLA 开发架构规范

结合 [alibaba/COLA](https://github.com/alibaba/COLA) 的「以业务为核心、解耦外部依赖、分离业务与技术复杂度」思想，落地到本仓库 **v2 Maven 多模块**结构。  
Maven 模块按业务域切分；**模块内**统一 `facade / application / domain / infrastructure` 分包。权威依赖方向见 `docs/refactor-v2/03-代码架构设计.md`，由 `ModuleArchitectureTest` 强制。

## 何时使用本 Skill

1. 新增 API / 领域能力 / Job / 工作流节点前：先定模块与层级。
2. Code review / 重构：检查是否穿透 Facade、是否跨域直接改表。
3. 用户问「这段逻辑放哪」「要不要新建模块」。

## 分层映射（COLA → Workbench v2）

| COLA 概念 | Workbench 落点 | 职责 |
|-----------|----------------|------|
| Adapter | `workbench-app` 的 `adapter.web` / `adapter.worker` | HTTP/SSE/Job 入参出参；**不写业务规则** |
| Application | 各域 `application`（`*FacadeImpl`、用例、`*Pipeline`）；app 仅薄壳 Handler | 编排、实现门面 |
| Domain | 各域 `domain.entity` / `domain.model` / `domain.rule` / `engine` / `runtime` | 实体、纯规则、图运行时 |
| Infrastructure | 各域 `infrastructure.mapper` / `assembler` / `client`；`workbench-infra` | 持久化与技术适配 |

### Maven 模块映射（按依赖层）

| 层 | 模块 | 放什么 |
|----|------|--------|
| 适配 | `workbench-app` | Controller、JobHandler 薄壳、Spring 配置、启动、种子 |
| 模式 | `workbench-mode-course` | 课程课表/生成流水线/节点/配图 |
| 模式 | `workbench-mode-hotnews` | 雷达、选题、简报、调研、热点文章与节点 |
| 内容核心 | `workbench-content` | 主题、作品、产出版本、媒体、`WorkContentStore`、`ModeDefinition` SPI |
| 内容核心 | `workbench-quality` | 审核、人工审核、修复、修复候选 |
| 内容核心 | `workbench-publishing` | 排版主题、渲染、渠道账号、`ChannelPublisher` / `OutputRenderer` |
| 内容核心 | `workbench-sourcing` | 信源频道、抓取运行、条目、拉黑、视频转写表 |
| 平台 | `workbench-notification` | 站内通知、`NotificationTargetResolver` SPI |
| 平台 | `workbench-skill-workflow` | Skill、工作流定义/运行、`WorkflowNodeHandler` 注册表 |
| 平台 | `workbench-file` / `job` / `ai` / `crawler` | 文件正文、任务队列、AI、抓取搜索 |
| 平台 | `workbench-common` / `infra` | 通用 DTO/异常；配置与存储 |

依赖只能**自上而下**：

```text
app → mode-* → content|quality|publishing|sourcing → notification|skill|file|job|ai|crawler|common|infra
```

同层仅允许：`quality → content`、`publishing → content`；`mode-course` 与 `mode-hotnews` 互不依赖。

禁止：Controller → Mapper；`app` 依赖 `*FacadeImpl`；domain 模块依赖 `workbench-app`；前端直连上游 AI；平台/核心反向依赖模式层（`quality → mode.hotnews.facade` 等已知暂免见 06）。

## 模块内包约定

```text
com.workbench.<module>
  facade/
    *Facade.java              # 仅门面接口（根包）；例外：WorkContentStore
    dto/request/ | dto/view/ | dto/job/
    enums/ | constant/
    spi/                      # ModeDefinition、ChannelPublisher、OutputRenderer、…
  application/                # *FacadeImpl、*Pipeline、*Store、prompt/
  domain/
    entity/ | model/ | rule/  # 及 engine/ | runtime/
  infrastructure/
    mapper/ | assembler/ | client/
```

`workbench-app`：

```text
com.workbench.app
  bootstrap/ | config/
  adapter.web.<域>/           # content|course|hotnews|quality|publishing|sourcing|…
  adapter.worker/             # JobHandler 薄壳 → 域内 Pipeline
```

**禁止**：把 Entity + Mapper + MyBatis-Plus `ServiceImpl` 按表合并成对外 CRUD 包；表操作由 `FacadeImpl` 编排、`Mapper` 持久化。

## 硬性规则

1. **Facade 门面**：域外只依赖 `facade..`（含子包 `dto`/`enums`/`constant`/`spi`），不依赖 `*FacadeImpl` / `infrastructure.mapper` / 他域 `domain.entity`。
2. **文章正文**：经 `WorkContentStore` 版本追加；禁止静默覆盖当前正文。
3. **AI 修复**：先存原文版本 → 再写修复版 → 复检；失败不覆盖原文。
4. **文件**：路径/`object_key` 只在 `stored_files`；业务表用 `file_id`。
5. **密钥与提示词**：不写日志、不进公开 API 响应。
6. **长任务**：`jobs` + 租约 + `job_events`/SSE；入出参用 `facade.dto.job` 类型。
7. **正式生成**：AI 失败或空结果必须报错，禁止用夹具正文、占位段落或占位图顶替。
8. **异常**：`BusinessException(code, message, httpStatus)`；**message 中文**，**code 英文**（集中常量）。
9. **禁止 `record`**：Facade 入参/出参与 DTO/视图用普通类（Lombok `@Data`/`@Builder` 等），单独建类文件；不要用 Java `record`，不要嵌套在接口内。
10. **禁止散落业务魔法值**：status/kind/protocol/runMode/节点 typeKey/scene 等用 `facade.enums` 或 `facade.constant`；闭集 `getValue`/`from`/`matches`，开集参照 `JobTypes`/`ModelCallScenes`。禁止 `"xxx".equals(typeKey)` 散落比较；同类型常量进同一常量类。
11. **上游 JSON**：用带注释的 POJO + Hutool `JSONUtil`，禁止 `createObj().set(...)` 拼报文。
12. **日志中文**：关键路径 `log.info/warn/error` 文案使用中文；MDC 必须有 `traceId`。
13. **API 信封**：对外 HTTP 使用 `ApiResult(code,msg,data)`；分页用 `PageQuery`/`PageResult`；禁止 Controller/Facade 公开 API 使用裸 `Map`（开放载荷用 `FlexiblePayload`）。
14. **禁止业务 Map 魔法键**：领域/应用逻辑内组装结构化数据须用带字段的普通类（`application.model` 或 `facade.dto.*`），禁止 `Map.put("title", …)` / `row.get("kind")` 这类字符串键；JSON 列持久化可用带注释的 POJO + `JSONUtil`，读取侧反序列化为类型，勿长期以 `Map<String,Object>` 流转。
15. **Controller 分包**：`adapter.web` 下按业务建子包（content/course/hotnews/quality/…），禁止巨石 DomainController。
16. **注释**：类、类属性、实体字段、FacadeImpl / Controller / Orchestrator / Handler 方法须有中文 JavaDoc（`@param`/`@return`）；方法内关键步骤与关键调用处中文行注释。注释不写对照旧实现来源。样板见 `DefaultAiFacade`。
17. **Facade 根包**：仅保留 `*Facade` 接口（及少量跨切面契约如 `WorkContentStore`）；DTO/enum/常量/SPI 必须放在子包，禁止扁平堆叠。一对多接口共用一个实现时，实现类命名为 `Default*` / 技术名，**禁止**无对应接口的 `XxxFacadeImpl`。
18. **持久化**：不引入按表 `IService`/`ServiceImpl` 充当领域服务；Entity 留在 `domain.entity`，Mapper 留在 `infrastructure.mapper`，聚合组装留在 `infrastructure.assembler`。
19. **类命名**：对外模型只用 `*View`/`*Result`/`*Request`/`*Patch`；禁止新 `*Dto`/`*Ref`/`*Hit`/`*Stats`；模块内协作用 `*Service`/`*Pipeline`/`*Store`；无状态纯函数用 `*Util`；SPI 用 `*Definition`/`*Publisher`/`*Renderer`/`*Resolver`；常量集中类不加 `Util`。详见 `docs/类命名词典.md`。
20. **命名可读性**：禁止无语境单字母变量（`a`/`p`/`v` 等）；用完整英语单词。
21. **可扩展设计**：可预见分支用策略/多态（节点 Handler、ModeDefinition、ChannelPublisher 等），避免巨型 switch 写死流水线；优先继承与组合表达变化。

## 公共依赖

- **Lombok** / **Hutool** / **MapStruct**：父 POM 全模块可用；实体↔视图用 MapStruct 或 Assembler；关键节点 `@Slf4j`。
- **时间类型**：实体与 DTO 统一 `LocalDateTime`（纯日期用 `LocalDate`）；禁止 `Instant` 作为字段/API 类型。
- **Swagger（springdoc）**：仅 `workbench-app`；类/方法用 `@Tag`/`@Operation`，字段可用 `@Schema`；开发环境 `/swagger-ui.html`，`prod` 关闭。
- DTO/视图：普通类，禁止 `record`（见硬性规则 9）；命名见硬性规则 19。
- 业务词表：见硬性规则 10；错误码见 `ErrorCodes`。
- API：见硬性规则 13–14。

## 验证

```powershell
. .\scripts\use-utf8.ps1
mvn -pl workbench-app -am test
# 架构守卫
mvn -pl workbench-app -am "-Dtest=ModuleArchitectureTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## 延伸阅读

- [reference.md](reference.md)
- [`docs/类命名词典.md`](../../../docs/类命名词典.md) — **类后缀权威词典**（View/Result/Request/Patch、Facade 一对一、Service/Util/SPI 边界）
- [`docs/类重命名清单-1-3.md`](../../../docs/类重命名清单-1-3.md) — 历史异名改造勾选
- `docs/代码结构与注释规范.md`
- `docs/refactor-v2/03-代码架构设计.md` — v2 目标模块与扩展点
- [alibaba/COLA](https://github.com/alibaba/COLA)
