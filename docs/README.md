# 成文坊文档索引

以仓库代码与测试为准。本目录只保留开源贡献与部署所需文档。

## 使用者

| 文档 | 说明 |
|------|------|
| [体验手册.md](./体验手册.md) | 从设置到第一篇成品的点击顺序（课程、热点加装、知识库、提示词包） |
| [../README.md](../README.md) | 一键安装（默认档是课程 + 知识库）与本机使用边界 |
| [../SECURITY.md](../SECURITY.md) | 鉴权始终开启；种子账号 `admin` / `admin123`（务必改密）；不要对公网裸暴露 |

## 给 AI 助手

| 文档 | 说明 |
|------|------|
| [AI从零搭建满血成文坊.md](./AI从零搭建满血成文坊.md) | 交给 AI 执行的满血搭建手册，不是人类主入口 |

## 贡献者

| 文档 | 说明 |
|------|------|
| [知识库RAG体系设计.md](./知识库RAG体系设计.md) | 知识库 RAG 当前行为（切块、Hybrid、拒答；Graph / Agent 默认关） |
| [知识库RAG成熟体系摘要.md](./知识库RAG成熟体系摘要.md) | RAG 双平面与分层检索摘要 |
| [信源沉淀池.md](./信源沉淀池.md) | 热点跨主题渠道池：标签、种子 JSON |
| [用户鉴权体系设计.md](./用户鉴权体系设计.md) | 单实例多用户鉴权（始终开启；Sa-Token + RBAC + owner_user_id；非 SaaS；U0–U4 已落地） |
| [代码结构与注释规范.md](./代码结构与注释规范.md) | COLA 分层、模块边界、注释与编码约定 |
| [类命名词典.md](./类命名词典.md) | Java 类后缀与 Facade 命名 |
| [../contracts/api-sse-contract.md](../contracts/api-sse-contract.md) | API / SSE 契约 |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | 贡献流程、技术栈与 CI |

架构说明也写在 [`.cursor/skills/chengwenfang-cola-architecture/`](../.cursor/skills/chengwenfang-cola-architecture/)。那是给贡献者助手用的 Agent Skill，不是产品里的提示词包。模块依赖由 `ModuleArchitectureTest` 强制。

产品名是提示词包。模块名是 `chengwenfang-prompt-workflow`。私有仓割接笔记不在公开树里，见 [`scripts/oss/README.md`](../scripts/oss/README.md)。
