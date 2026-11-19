# 成文坊文档索引

以仓库代码与测试为准。本目录只保留开源贡献与部署所需文档。

| 文档 | 说明 |
|------|------|
| [AI从零搭建满血成文坊.md](./AI从零搭建满血成文坊.md) | 给 AI / 人类的从零满血部署手册（含 macOS Apple Silicon 附录、Qdrant / 知识库） |
| [信源沉淀池.md](./信源沉淀池.md) | 热点跨主题渠道池：标签、种子 JSON、bootstrap 导入 |
| [知识库RAG体系设计.md](./知识库RAG体系设计.md) | 知识库 RAG 权威设计（切块、Hybrid、拒答、Graph / Agent 开关） |
| [知识库RAG成熟体系摘要.md](./知识库RAG成熟体系摘要.md) | RAG 双平面与分层检索摘要 |
| [代码结构与注释规范.md](./代码结构与注释规范.md) | COLA 分层、模块边界、注释与编码约定 |
| [类命名词典.md](./类命名词典.md) | Java 类后缀与 Facade 命名 |
| [../contracts/api-sse-contract.md](../contracts/api-sse-contract.md) | API / SSE 契约 |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | 贡献流程 |
| [../SECURITY.md](../SECURITY.md) | 本机部署安全与采集合规边界 |

架构细节另见 [`.cursor/skills/chengwenfang-cola-architecture/`](../.cursor/skills/chengwenfang-cola-architecture/)。模块依赖由 `ModuleArchitectureTest` 强制。

产品定位与界面能力见根目录 [`README.md`](../README.md)。私有仓割接笔记、`docs/refactor-v2` 等故意不纳入公开树，见 [`scripts/oss/README.md`](../scripts/oss/README.md)。
