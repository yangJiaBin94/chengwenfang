# 工作台数据库迁移至 PostgreSQL 设计方案

## 1. 文档目标

将工作台运行数据库从本地 SQLite 迁移至 PostgreSQL，在保持课程体系、热点资讯、资料池、文章工作区、审核、排版和任务状态兼容的前提下，解决 SQLite 单写入锁造成的任务排队与 `database is locked`，为多 Worker 并行执行提供可靠基础。

本文档覆盖架构调整、数据迁移、任务并发、环境配置、测试、切换、回滚和验收，不在本阶段引入 Redis、Celery 或新的业务流程。

## 2. 当前现状

### 2.1 数据库配置

- 默认数据库固定为 `.chengwenfang/workbench.db`。
- `Settings.database_url` 通过属性拼接 SQLite URL，不能由环境变量直接覆盖。
- SQLAlchemy 创建引擎时固定传入 `check_same_thread=False`。
- 每个连接固定执行 `foreign_keys`、`WAL`、`busy_timeout` 三项 SQLite PRAGMA。
- Alembic 启动时读取同一个运行时数据库 URL。

### 2.2 后台任务

- Worker 使用数据库表 `jobs` 作为持久任务队列。
- 当前 Worker 串行领取任务，一次只执行一个任务。
- 课程生成、图片生成、热点采集和资料核验都会持续更新任务进度。
- 多 Worker 同时运行时已出现 SQLite 写锁冲突。
- 当前为了稳定使用单 Worker，因此长课程任务会阻塞热点资料核验。

### 2.3 测试与迁移

- 绝大多数单元测试使用临时 SQLite 数据库，速度快且隔离简单。
- 迁移测试直接使用 `sqlite3`、`PRAGMA table_info` 和 `sqlite_master`。
- 当前 Alembic 迁移头为 `0046_remove_mdnice_exports`。
- 模型使用 SQLAlchemy 通用 `JSON`、`DateTime(timezone=True)`、字符串主键和外键，主体结构可兼容 PostgreSQL。

## 3. 目标架构

```text
React / Vite
      |
FastAPI API 进程
      |
PostgreSQL 16
      |
Worker 1  Worker 2  Worker 3
课程任务  热点任务  通用任务
```

目标要求：

1. PostgreSQL 成为开发和正式运行的默认数据库。
2. SQLite 仅保留给快速单元测试和一次性旧数据导入。
3. API 与多个 Worker 共享 PostgreSQL 连接池。
4. Worker 使用行级锁安全领取任务，不重复执行同一任务。
5. 网络请求和 AI 请求期间不持有数据库事务锁。
6. 现有业务 ID、版本关系、任务历史和文章数据完整保留。
7. 切换失败时可在规定窗口内回退到只读冻结的 SQLite 备份。

## 4. 产品与技术边界

本次包含：

- PostgreSQL 本地开发环境。
- 数据库连接配置重构。
- Alembic PostgreSQL 兼容。
- SQLite 到 PostgreSQL 的一次性数据迁移工具。
- 多 Worker 安全领取和并发配置。
- 数据一致性校验、切换和回滚。

本次不包含：

- Redis、RabbitMQ 或 Kafka。
- Celery、RQ 等新任务框架。
- 业务表重命名或领域模型重构。
- 公众号官方发布能力。
- 跨机器高可用 PostgreSQL 集群。

## 5. 环境方案

### 5.1 PostgreSQL 版本

推荐 PostgreSQL 16，编码固定为 UTF-8，时区固定为 UTC。

开发环境使用 Docker Compose：

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: workbench
      POSTGRES_USER: workbench
      POSTGRES_PASSWORD: chengwenfang_dev
      TZ: UTC
    ports:
      - "5432:5432"
    volumes:
      - chengwenfang-postgres:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U workbench -d workbench"]
      interval: 5s
      timeout: 3s
      retries: 20
```

生产环境密码不得写入仓库，必须由部署环境注入。

### 5.2 配置变量

新增：

```env
CHENGWENFANG_DATABASE_URL=postgresql+psycopg://workbench:password@127.0.0.1:5432/workbench
CHENGWENFANG_DATABASE_POOL_SIZE=10
CHENGWENFANG_DATABASE_MAX_OVERFLOW=20
CHENGWENFANG_DATABASE_POOL_TIMEOUT=30
CHENGWENFANG_WORKER_CONCURRENCY=3
```

`Settings` 将 `database_url` 改为真正可配置字段。仅当变量未提供且运行测试时，才允许显式传入 SQLite URL。

## 6. 连接层改造

### 6.1 依赖

增加：

```text
psycopg[binary]>=3.2,<4
```

### 6.2 引擎创建

`create_database_engine()` 按 URL 方言分支：

- SQLite：保留 `check_same_thread` 和 PRAGMA，仅供测试与导入。
- PostgreSQL：启用 `pool_pre_ping`、连接池大小、溢出连接和超时。
- 不允许向 PostgreSQL 连接执行 SQLite PRAGMA。

建议结构：

```python
if url.startswith("sqlite"):
    engine = create_engine(url, connect_args={"check_same_thread": False})
    event.listen(engine, "connect", configure_sqlite)
else:
    engine = create_engine(
        url,
        pool_pre_ping=True,
        pool_size=settings.database_pool_size,
        max_overflow=settings.database_max_overflow,
        pool_timeout=settings.database_pool_timeout,
    )
```

API 请求继续使用请求级 Session。Worker 每次领取任务和执行任务分别使用明确生命周期的 Session。

## 7. Schema 兼容审计

### 7.1 可直接兼容

- `String`、`Text`、`Integer`、`Boolean`。
- 字符串 UUID 主键。
- SQLAlchemy `JSON`。
- 外键与唯一约束。
- 普通索引。

### 7.2 必须验证

1. 所有 `DateTime(timezone=True)` 在 PostgreSQL 中保存为 `TIMESTAMPTZ`。
2. 业务代码不得再依赖 SQLite 返回 naive datetime。
3. JSON 默认值必须由 Python callable 提供，不使用数据库字符串默认值。
4. 布尔字段查询不得依赖 SQLite 的 `0/1` 隐式转换。
5. 模糊匹配、大小写比较和排序结果需要确认 PostgreSQL 行为。
6. 删除迁移中的外键顺序必须在 PostgreSQL 下可执行。
7. 长索引字段长度和唯一索引需要检查 PostgreSQL B-tree 限制。

### 7.3 暂不切换 JSONB

首轮迁移继续使用 SQLAlchemy `JSON`，减少 Schema 差异。稳定后可单独评估将高频查询字段改为 `JSONB` 并增加 GIN 索引。

## 8. Alembic 改造

### 8.1 原则

- 保留现有 `0001` 至 `0046` 迁移链。
- 所有迁移必须能在空 PostgreSQL 数据库执行到 head。
- 不修改已执行迁移的 revision ID。
- 如旧迁移存在 SQLite 专属操作，使用 `context.get_context().dialect.name` 做兼容分支。

### 8.2 新迁移

新增 PostgreSQL 基线兼容迁移 `0047_postgresql_compatibility`，只处理确实需要的类型、默认值、约束或索引，不承载旧数据导入。

### 8.3 迁移测试

测试分两层：

- SQLite 快速迁移测试继续保留，验证旧数据库可升级。
- PostgreSQL 集成测试从空库执行 `alembic upgrade head`，再执行关键表、外键、索引和回滚边界检查。

禁止继续只通过 `sqlite_master` 判断迁移正确性。跨方言检查使用 SQLAlchemy Inspector。

## 9. 旧数据迁移方案

### 9.1 迁移策略

采用项目内 Python 数据迁移工具，不直接依赖 pgloader。原因：

- 项目包含大量 JSON、时间、外键和历史任务关系。
- 需要逐表校验和可重复执行。
- Windows 开发环境部署 pgloader 成本较高。
- 项目需要对乱码历史字段和 naive datetime 做显式处理。

脚本建议：

```text
backend/scripts/migrate_sqlite_to_postgres.py
```

参数：

```text
--source sqlite+pysqlite:///.../.chengwenfang/workbench.db
--target postgresql+psycopg://...
--verify-only
--truncate-target
--batch-size 500
```

### 9.2 表迁移顺序

按外键依赖排序：

1. 配置与模式：AI 配置、内容模式、主题、工作区。
2. 课程主数据：学习主题、系列、课程。
3. 内容数据：文章、文章版本、生成设置。
4. Skill 与工作流定义。
5. 任务、工作流运行、节点、事件、模型调用。
6. 图片、审核、人工审核、发布主题与快照。
7. 热点来源、采集批次、来源条目、热点聚类。
8. 选题证据、简报、资料池、冲突证据。
9. 热点文章、版本、改写、审核和发布快照。
10. 通知与其他附属记录。

所有主键保持原值，不重新生成 ID。

### 9.3 数据转换

- datetime：无时区值按 UTC 解释，统一写入 aware UTC datetime。
- JSON：读取后必须是 dict/list，再交由 PostgreSQL 驱动序列化。
- Boolean：显式转换为 Python bool。
- 空字符串和 NULL：保持现有业务语义，不做全局替换。
- 文本：统一 UTF-8，发现不可解码或乱码时记录表名、主键和字段。

### 9.4 幂等性

- 默认要求目标数据库为空。
- 每张表按主键插入。
- 脚本保存迁移清单和每表计数。
- 失败后允许清空目标库重新执行，不在半迁移数据库上盲目续跑。

## 10. 数据校验

迁移完成后自动输出报告：

```text
.chengwenfang/migration-reports/postgresql-migration-YYYYMMDD-HHmmss.json
```

校验内容：

1. 每张表源端和目标端行数一致。
2. 每张表主键集合一致。
3. 关键外键无孤儿记录。
4. JSON 字段可解析率 100%。
5. 课程、文章、版本、热点、简报、资料条目总数一致。
6. 每类任务状态数量一致。
7. 抽样内容哈希一致。
8. Alembic 版本为目标 head。

关键业务抽样：

- 随机打开 5 门课程及其文章版本。
- 打开 5 个热点及其来源证据。
- 打开 3 个选题资料池和简报。
- 打开 3 篇热点文章及发布快照。
- 验证 AI 配置只显示“已配置”，不在报告中输出密钥。

## 11. Worker 并发改造

### 11.1 安全领取

PostgreSQL 下使用：

```sql
SELECT id
FROM jobs
WHERE status = 'pending'
ORDER BY priority DESC, created_at ASC
FOR UPDATE SKIP LOCKED
LIMIT 1;
```

在同一短事务内将任务更新为 running 并提交。多个 Worker不会领取同一个任务，也不会互相等待行锁。

### 11.2 队列优先级

为 `jobs` 增加 `priority` 字段：

| 任务类型 | 默认优先级 |
|---|---:|
| 热点资料核验 | 80 |
| 热点采集 | 70 |
| 热点文章改写 | 65 |
| 热点文章生成 | 60 |
| 课程文本生成 | 50 |
| 图片生成 | 40 |

优先级只影响尚未领取的任务，不中断正在执行的任务。

### 11.3 并发数

第一阶段推荐并发数 `3`：

- Worker A：通用文本与课程任务。
- Worker B：热点采集与资料核验。
- Worker C：图片和其他任务。

可先使用相同 Worker进程和数据库筛选规则，不立即引入外部消息队列。

### 11.4 事务边界

- 领取任务：短事务。
- 更新进度：独立短事务。
- 调用 AI、下载网页、生成图片：不得保持未提交写事务。
- 保存最终结果：短事务并进行实体状态校验。
- 终态通知失败不得回滚已成功的业务结果。

## 12. API 与前端影响

业务 API 地址和响应结构原则上不变。

前端继续展示：

- 等待原因。
- 当前步骤。
- 进度百分比。
- 完成或失败通知。

迁移后多个任务可并行，页面不得假设“只有一个 running job”。通知中心和任务详情必须按 job ID读取状态。

## 13. 开发启动方式

建议命令：

```powershell
docker compose up -d postgres
cd backend
python -m alembic upgrade head
cd ..
.\scripts\dev.ps1 -Service all
```

`dev.ps1` 启动前执行 PostgreSQL 健康检查。数据库不可用时应明确报错，不允许静默回退到 SQLite，以免数据写入错误数据库。

## 14. 切换步骤

### 阶段 A：兼容开发

1. 增加 psycopg 和可配置数据库 URL。
2. 调整连接层方言分支。
3. 让全量 Alembic 在 PostgreSQL 空库通过。
4. 业务测试继续使用 SQLite，新增 PostgreSQL 集成测试。

### 阶段 B：迁移工具

1. 实现按依赖顺序导入。
2. 实现时间、JSON 和布尔转换。
3. 实现计数、主键和哈希校验。
4. 使用 SQLite 备份进行至少两次演练。

### 阶段 C：任务并发

1. 增加 priority。
2. PostgreSQL 使用 `SKIP LOCKED` 领取。
3. 拆短 AI/网络请求周围的事务。
4. 并发从 2 开始压测，稳定后提升至 3。

### 阶段 D：正式切换

1. 停止 API 和 Worker，进入维护窗口。
2. 复制 SQLite 文件并计算 SHA-256。
3. 对 PostgreSQL 执行迁移到 head。
4. 执行数据导入与完整校验。
5. 切换 `CHENGWENFANG_DATABASE_URL`。
6. 先启动 API 并进行只读冒烟检查。
7. 再启动单 Worker验证任务领取。
8. 最后增加到目标 Worker数量。

## 15. 回滚方案

切换前 SQLite 文件进入只读冻结，不再允许两个数据库双写。

触发回滚条件：

- 关键表计数或主键不一致。
- 课程、热点或文章核心链路不可读。
- 多 Worker出现重复领取或任务丢失。
- PostgreSQL 持续不可用且无法在维护窗口内恢复。

回滚步骤：

1. 停止 API 和全部 Worker。
2. 保存 PostgreSQL 失败现场和日志。
3. 将数据库 URL恢复为冻结的 SQLite 备份。
4. 只启动单 Worker。
5. 验证健康检查和核心页面。

切换后如果 PostgreSQL 已产生新写入，不允许直接回滚到旧 SQLite，否则会丢失新数据。此时必须先导出增量或延长维护窗口处理。

## 16. 安全要求

- 数据库密码仅通过环境变量或密钥管理注入。
- PostgreSQL 仅监听需要的网络接口。
- 正式环境强制 TLS 或位于可信内网。
- 应用账号不使用 PostgreSQL 超级用户。
- 迁移报告不得包含 API Key、文章敏感配置或数据库密码。
- 备份文件和迁移日志位于 `.chengwenfang`，不得提交 Git。

## 17. 测试计划

### 17.1 自动化测试

- PostgreSQL 空库 Alembic upgrade head。
- SQLite 旧库升级到最新 head。
- SQLite 到 PostgreSQL 数据迁移。
- 关键表计数、主键、外键和 JSON 校验。
- 两个 Worker并发领取 100 个测试任务，无重复、无遗漏。
- Worker 崩溃后租约恢复。
- API 与 Worker同时更新任务时无死锁。
- 热点采集与课程生成同时执行。
- 资料核验和图片生成同时执行。

### 17.2 业务验收

- 课程生成、版本保存、审核和排版可用。
- 热点采集、确认选题、资料核验、简报和文章生成可用。
- 任务进度、通知和失败重试可用。
- 多任务并行时页面显示各自真实状态。

### 17.3 性能门槛

- 普通列表 API P95 小于 500ms。
- 任务领取 P95 小于 100ms。
- 三个 Worker并行运行 30 分钟无死锁、重复领取和连接池耗尽。
- PostgreSQL 连接数不超过配置上限。

## 18. 监控与运维

至少记录：

- 数据库连接池使用量。
- 活跃连接和等待连接。
- 任务排队数量与最长等待时间。
- 每种任务执行时长。
- 数据库死锁与事务重试次数。
- Worker心跳和租约恢复次数。

备份建议：

- 每日 `pg_dump`。
- 保留最近 7 个日备份。
- 正式切换前单独保存一份 SQLite 原文件和 PostgreSQL 初始 dump。
- 每月执行一次恢复演练。

## 19. 实施清单

| 阶段 | 交付物 | 完成标准 |
|---|---|---|
| 1. 配置与连接 | psycopg、环境变量、方言分支 | API 可连接 PostgreSQL |
| 2. Schema 兼容 | PostgreSQL 全迁移链 | 空库升级到 head |
| 3. 数据迁移 | 导入脚本和校验报告 | 全表计数与关键哈希一致 |
| 4. Worker 并发 | SKIP LOCKED、优先级、短事务 | 3 Worker无重复领取 |
| 5. 集成测试 | PostgreSQL 测试环境 | 核心业务与并发测试通过 |
| 6. 正式切换 | 备份、导入、配置切换 | PostgreSQL 成为唯一写库 |
| 7. 观察期 | 监控和回滚窗口 | 连续 7 天稳定运行 |

## 20. 最终验收标准

只有同时满足以下条件才视为迁移完成：

1. PostgreSQL 是唯一运行写库。
2. SQLite 数据全部迁移并通过校验报告。
3. 所有核心业务流程通过。
4. 至少 3 个 Worker可稳定并发。
5. 任务无重复领取、无永久 running、无丢失。
6. 热点资料核验不再因课程生成而长时间排队。
7. 自动备份和恢复演练可用。
8. SQLite 仅作为冻结备份和测试数据库存在。

