# 知识库 RAG 成熟体系摘要

与 Cursor 画布 `mature-rag-architecture` 同一叙事，便于仓库内链接；完整契约与里程碑见 [知识库RAG体系设计.md](./知识库RAG体系设计.md)。

## 核心结论

成熟体系 = **知识平面 + 在线回答平面**；默认 **Hybrid →（可选）Rerank → 强制引用/拒答**；记忆分层；**Graph / Agentic 按需、有界**；评测与 trace 当门禁。

## 分层检索

| 层 | 作用 |
|---|---|
| L0 路由 | 选库 / 选模式（Hybrid / Local / Global / Agent） |
| L1 Hybrid | 向量 + 全文 + **检索侧**元数据过滤 |
| L2 结构扩展 | 父子块、contextual 前缀 |
| L3 关系 | Neo4j Local / Global（非默认） |
| L4 Agentic | 有界迭代：检索 → 评证据 → 再检 |

## 存储分工（不可互代）

- **Qdrant**：块向量、社区摘要向量  
- **Neo4j**：实体、边、社区、多跳  
- **MySQL**：文档/chunk 元数据、会话记忆、`indexVersion` 读指针  

## 幻觉治理

检索门槛 → grounded 生成 → 引用校验 → 不足拒答；图边带置信度与纠错，把关系错误前移到可审计入库。

## 记忆

工作窗 / 会话摘要 / 长期语义 / 事件日志；多轮用摘要 + 检索式回忆，不整段历史塞窗。

## 启用纪律

金标分档（简单事实 / 多跳 / 应拒答）驱动开 Graph / Agent；feature flag；Neo4j 仅 `docker compose --profile neo4j` 启动。
