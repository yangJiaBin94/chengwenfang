# 知识库 RAG 成熟体系摘要

完整字段与开关见 [知识库RAG体系设计.md](./知识库RAG体系设计.md)。

默认开启：入库切块、Hybrid、改写、重排、引用与拒答、会话、反馈、记忆。

需配置：Qdrant（随默认 `docker compose up`）和嵌入 Key。未配嵌入 Key 不能把索引或问答当成成功。

默认关闭：Graph 与 Agentic。质量模块的写作规范审核是另一条链路。

## 核心结论

知识平面负责入库，在线回答平面负责检索和生成。默认路径是 Hybrid，然后可选重排，再强制引用或拒答。记忆分会话摘要和「请记住」。Graph / Agentic 有界，开关默认关。

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

## 开关

简单事实和应拒答走默认 Hybrid。多跳才考虑打开 Graph。Neo4j 容器用 `docker compose --profile neo4j` 或满血档的 `--profile all`；容器在跑也不等于 `knowledge.graph.enabled=true`。Agent 同样默认关。
