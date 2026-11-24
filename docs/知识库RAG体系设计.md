# 知识库 RAG 体系设计

本文描述知识库现在怎么工作。摘要见 [知识库RAG成熟体系摘要.md](./知识库RAG成熟体系摘要.md)。

默认开启：入库切块、Hybrid 检索、查询改写、重排、引用与拒答、会话、反馈、「请记住」记忆。

需配置：Qdrant（默认 `docker compose up` 已包含）和设置里的嵌入 Key。未配嵌入 Key 时索引与问答失败，系统就绪不通过。

默认关闭：Graph（Neo4j）与 Agentic。写作规范审核在质量模块，不走这套 RAG。

## 1. 目标与非目标

**目标**

- 写作素材与跨作品问答：有出处、可拒答、可重建。
- 默认路径是 Hybrid。Graph 与 Agentic 有实现，开关默认关。

**非目标**

- 不上外部 rerank SaaS，不以 Global GraphRAG 为默认问答路径。
- 不把质量模块的规范审核并进知识库问答。

## 2. 双平面与数据契约

### 知识平面

发现变更 → 解析 → 规范化 → 切块（含 provenance）→ embed → Qdrant；可选抽边 → Neo4j → 社区摘要 embed。

### 在线回答平面

鉴权/过滤 → 查询理解 → 路由 → 检索 → 可选 rerank → 证据门 → 生成 → 引用校验 → 返回（或拒答）。

### 关键字段

| 对象 | 字段 | 说明 |
|---|---|---|
| Chunk | `charSpanStart` / `charSpanEnd` | 原文偏移，忠实度回溯 |
| Chunk | `indexedAt` | 索引时间 |
| Chunk / Qdrant / Neo4j | `indexVersion` | 蓝绿版本号 |
| Chunk | `parentChunkId` | 父子块：child 检索，parent 进窗 |
| 全局 | `knowledge_index_state.active_version` | 读路径活跃版本 |
| 边 | `confidence` / `enabled` / `sourceChunkId` | 抽边审计与纠错 |

## 3. 分层检索与路由（写死）

1. **默认 Hybrid**（向量 + FULLTEXT/LIKE），过滤在检索侧（Qdrant payload + SQL）。
2. 问法命中关系/实体邻域 → **Local Graph**（需 `graph.enabled`）。
3. 问法命中整体/对比/演进/社区主题 → **Global**（社区摘要向量 + 下钻）。
4. 固定管线证据不足且 `agent.enabled` → **Agentic**（有界）。

禁止：整库问答默认 Global。

## 4. 幻觉治理

当前问答要求：

- 无证据则拒答，并给出引用。
- 关键结论可回溯到 citation span（忠实度校验默认开，可关）。
- 图边只在 Graph 打开后参与。低置信边默认禁用；纠错后按 `sourceChunkId` 增量重建。

拒答文案约定：`知识库中未找到足够相关的内容，无法基于现有资料回答。请换一个与已入库作品相关的问题。`（固定短语，见 `KnowledgeRagConstants.ABSTAIN_MESSAGE`）。

这和课程 / 热点文的质量审核不是同一条链路。审核看写作规范，不靠这次检索。

## 5. 记忆分层

| 层 | 载体 | 用途 |
|---|---|---|
| 工作窗 | 本轮 prompt | 问题 + 证据 + 必要指令 |
| 会话工作记忆 | `knowledge_chat_sessions.summary` | 滚动摘要，解指代 |
| 长期记忆 | `knowledge_memories` | 偏好/结论，带 provenance 与 TTL |
| 事件真相 | 消息表 / 观测日志 | 回放与排障 |

开关：`chengwenfang.knowledge.memory.enabled`；关闭则单轮。

## 6. Neo4j 图模型与操作面

- 节点标签闭集：`Concept` / `Work` / `Theme` / `Community`（实现以枚举为准）。
- 关系类型闭集：`MENTIONS` / `RELATED_TO` / `PART_OF` 等。
- 属性：`indexVersion`、`sourceChunkId`、`confidence`、`enabled`。
- **Docker**：`compose.yml` profile `neo4j`；端口 7474/7687；默认 `up` 不启动。
- **操作面**：置信度；低置信默认 `enabled=false`；人工/规则纠错 API；按 `sourceChunkId` 删除旧边并重抽该块邻域。

## 7. Agentic

- 配置：`maxRounds`（默认 3）、超时、`tokenBudget`。
- **提前停**：已有足够证据；连续两轮命中无增量；预算将尽。
- 工具：`hybrid_search` / `graph_local` / `graph_global` / `rewrite`。
- 简单问必须固定管线；前端标明模式与轮次。

## 8. 评测与金标分档

| 档 | 用途 |
|---|---|
| 简单事实 | 默认 Hybrid 路径 |
| 多跳关系 | 考虑打开 Graph 之前先看这一档 |
| 应拒答 | 默认路径必须能拒答 |

Rerank：默认可开 LLM listwise，**必须可关**；始终保留无 rerank 基线对比 Recall/延迟/费用。

资源目录：`chengwenfang-knowledge/src/test/resources/rag-golden/`。

## 9. 可观测性

按 `sessionId` 可追查：`query`、`rewrittenQuery`、`mode`、`round`、命中 `chunkId`、融合分、是否 rerank、是否走图、拒答原因、耗时、token。

落表：`knowledge_retrieval_traces`（或消息 `payload_json.trace`）。

## 10. 版本蓝绿

1. 写入使用 `nextVersion = active + 1`（或时间戳版本串）。
2. 向量点与图节点带同一 `indexVersion`。
3. 金标门禁通过后更新 `knowledge_index_state.active_version`。
4. 读路径只读活跃版本；失败不切流。

图重建与向量重建对齐：先向量版本就绪，再图版本就绪，最后原子切 `active_version`（设计允许「图滞后一拍」但读路径不得混版本）。

## 11. Feature flags（application.yml）

```yaml
chengwenfang:
  knowledge:
    graph:
      enabled: false
    agent:
      enabled: false
      max-rounds: 3
      token-budget: 8000
    rerank:
      enabled: true
    memory:
      enabled: true
  neo4j:
    uri: bolt://127.0.0.1:7687
    username: neo4j
    password: ${NEO4J_PASSWORD:chengwenfang_neo4j}
```

## 12. 当前行为

| 能力 | 现在 |
|---|---|
| 拒答、引用、检索侧过滤、`indexVersion` / `charSpan` | 默认路径 |
| 可关的 rerank、span 忠实度 | 默认开，配置可关 |
| 会话摘要与「请记住」 | 默认开；关掉记忆则退回单轮 |
| Neo4j、多跳、图边纠错 | 代码在；`knowledge.graph.enabled` 默认 false。`docker compose --profile all` 可以拉起 Neo4j 容器，应用不会因此打开 |
| 有界 Agent、提前停 | 代码在；`knowledge.agent.enabled` 默认 false。简单问走固定管线 |

## 13. 手动摄入与切块形态

与默认问答路径同时存在：手动上传、网页，以及作品 / 热点资料登记。

### 摄入

- 来源：`work_output`（自动）+ `manual_upload`（上传/URL）+ `source_item` / `research_item` / `radar_topic`（登记时写入 `text_file_id`；雷达 Top10 选题为 `radar_topic`）。
- 上传白名单：txt/md/docx/pdf（数字文本层）及增强 pptx/xlsx/csv/图片标注。
- 流程：存 `origin_file_id` → Job 索引 → Extractor 归一化 → 回写 `text_file_id` → 按 `chunkProfile` 切块 → embed。
- 抽取不足 50 字或空 PDF/扫描件 → `failed`，不入向量。
- Citation：展开摘录为解析文本；无深链时文案标明「摘自解析文本」，不做 PDF 页码映射。

### `chunkProfile`（切块形态）

索引管线按 `KnowledgeChunker.supports(profile)` 注册表路由，**禁止**用裸 `mediaKind=document` / 扩展名选 chunker。Extractor 声明 `chunkProfile`；滑窗参数由 `ChunkParamsResolver` 给出（默认 600/800/80/120 合并阈值；`mediaKind=video` 时 overlap=120）。

| 值 | Chunker | 契约要点 |
|---|---|---|
| `markdown_structured` | MarkdownChunker | ATX 分节 + 节内 `TextWindowSplitter`；`headingPath`；charSpan；弱 parent |
| `plain` | PlainTextChunker | 段落聚合 + 滑窗；短文单块；charSpan；视频更大 overlap |
| `table_rows` | TableRowChunker | 一行一块、禁止横切行；`## sheet` → headingPath；行数上限 20000 |
| `slide` | SlideChunker | `## 第 N 页` 一页一块；空页跳过；超长仅页内窗；无页标题回退 Plain |

归一化约定：xlsx/csv 写出 `## sheet` + 空行分隔的 `列:值` 行；pptx 跳过空页，备注写入 `备注:`。docx 转 MD 后标 `markdown_structured`，多数 PDF 标 `plain`。

### 切块策略版本

- 常量 `KnowledgeChunkStrategyVersions.CURRENT`（当前 `c2`）；落库字段 `knowledge_documents.chunk_strategy_version`。
- `shouldSkip`：`contentHash` 相同、策略版本等于 CURRENT、且文档 `chunkCount` 与切块表（当前 active 版本）行数一致才跳过；null/旧版本/块数不一致必须重切。
- 算法或契约变更时 **bump VERSION**，不要只改代码指望 hash 变化。
- 同一 `chunkProfile` 只允许注册一个 `KnowledgeChunker`；重复则索引失败。

### 防坑

1. 不用扩展名/裸 DOCUMENT 选 chunker。
2. 表不中切行、不丢列名；CSV 引号感知；UTF-8 BOM / 非法 UTF-8 回退 GB18030。
3. 幻灯不跨页 overlap。
4. Citation 指向解析文本，不做 PDF 页码深链。
5. 空/扫描 PDF 不足 50 字 → failed。
6. 字符尺寸非 tokenizer；embedding 过长只截断 `embeddingText`。
7. Chunker 互相禁止 `new`；参数常量集中在 `KnowledgeChunkConstants`。
8. 弱 `parentChunkId` 非 Dify 级父子，不当产品卖点。
9. 表格超 `MAX_TABLE_ROWS` → `knowledge_table_too_large`（HTTP 400）。

## 14. 附录：名词表

见摘要文档与对话约定：Hybrid、Rerank、Local/Global、Neo4j vs Qdrant、拒答、金标、蓝绿、Agentic、chunkProfile。
