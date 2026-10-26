# Workbench × COLA 参考手册

本文件补充 [SKILL.md](SKILL.md)。上游思想来自 [alibaba/COLA](https://github.com/alibaba/COLA)。目标结构见 `docs/refactor-v2/03-代码架构设计.md`。

## 1. 包结构（v2）

### 领域 / 平台模块

```text
com.workbench.<module>
  facade/
    *Facade.java | WorkContentStore（跨切面契约例外）
    dto/request/ | dto/view/ | dto/job/
    enums/ | constant/ | spi/
  application/            # *FacadeImpl、*Pipeline、*Store、prompt/
  domain/entity|model|rule|engine|runtime/
  infrastructure/mapper|assembler|client/
```

跨模块只依赖 `facade..`（含子包）。**禁止** Entity+Mapper+`ServiceImpl` 按表合成对外包。

### workbench-app

```text
com.workbench.app
  WorkbenchApplication
  bootstrap/ | config/
  adapter.web.<域>/       # Controller、ExceptionHandler
  adapter.worker/         # JobHandler 薄壳
```

### workbench-infra

```text
com.workbench.infra
  config/                 # WorkbenchProperties、AutoConfiguration
  support/                # StorageService
```

## 2. 依赖规则

- `adapter.*` → 各域 `facade.*` + 平台 `facade.*`（Worker 可注入域内 `*Pipeline`）
- `application.*`（app）→ `facade.*`，禁止 `*FacadeImpl`、禁止他域 `domain.entity` / `infrastructure.mapper`
- 域内 `application` → 本域 `domain` + `infrastructure` + 平台 Facade
- 自上而下：`app → mode → content|quality|publishing|sourcing → platform`
- 同层仅：`quality → content`、`publishing → content`；模式层互不依赖
- 已知暂免（D-21～D-32）见 `docs/refactor-v2/06-进度与决策记录.md`

守卫测试：`ModuleArchitectureTest`。

## 3. 用例走线

### HTTP 读课程

```text
adapter.web.content.CourseController → content.facade.ContentFacade → infrastructure.mapper
```

### 课程长文生成

```text
Controller 创建 Job
  → adapter.worker.Handler → mode.course.application.generation.*Pipeline
    → skill/ai/content Facades → job_events / NotificationFacade
```

### 热点抓取

```text
HotNewsFacade.startFetch → jobs
  → HotNewsFetchHandler → RadarCollectPipeline → Sourcing/HotNews Facades
```

## 4. 数据与迁移

- Flyway 仅在 `workbench-app/.../db/migration`：合并后为 `V1__schema.sql` + `V2__dictionaries.sql`。
- `@MapperScan` 指向 `com.workbench.*.infrastructure.mapper`。
- 表结构与字典约定见 `docs/refactor-v2/02-新表结构设计.md`。

## 5. Review 清单

- [ ] 新类型落在正确模块与 package（模式 / 内容核心 / 平台）
- [ ] app 无 FacadeImpl / 领域 Mapper / 他域 Entity 依赖
- [ ] 长任务走 Job；载荷用 `facade.dto.job`
- [ ] 正文经 `WorkContentStore`；扩展点走 `facade.spi`
- [ ] `ModuleArchitectureTest` 通过
