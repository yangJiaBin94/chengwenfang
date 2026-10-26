# Workflow parity inventory (Java vs Python backup)

Source of truth: `backup/main-20260907-before-java-rewrite`  
`backend/app/services/workflow_catalog.py`, `skill_nodes.py`, `workflow_nodes.py`, `workflow_management.py`  
FE: [`src/workflow-client.ts`](../src/workflow-client.ts), [`src/WorkflowPage.tsx`](../src/WorkflowPage.tsx)

## Node catalog

| Key | Category | Model | Java before this work | Target |
|-----|----------|-------|------------------------|--------|
| `start` | control | none | present (minimal) | keep |
| `end` | control | none | present | keep |
| `llm_text` | llm | text | present | keep (generic) |
| `draft_opening` | 课程原稿 | text | missing | implement |
| `draft_core` | 课程原稿 | text | missing | implement |
| `draft_example` | 课程原稿 | text | missing | implement |
| `draft_summary` | 课程原稿 | text | missing | implement |
| `draft_preview` | 课程原稿 | text | missing | implement |
| `wechat_conversion` | 公众号 | text | missing | implement |
| `image_cover` | 课程图片 | image | missing | implement |
| `image_inline` | 课程图片 | image | missing | implement |
| `formula_style_validation` | 质量检查 | none | missing | implement |
| `formula_expression_validation` | 质量检查 | none | missing | implement |
| `final_validation` | 质量检查 | none | missing | implement |
| `commit_version` | 正式保存 | none | missing | implement |
| `controlled_condition` | 流程控制 | none | missing | implement |
| `bounded_loop` | 流程控制 | none | missing | implement |
| `hot_news_load_context` | 热点结构 | none | missing | implement |
| `hot_news_prepare_facts` | 热点结构 | none | missing | implement |
| `hot_news_final_validation` | 热点结构 | none | missing | implement |
| `hot_news_commit_version` | 热点结构 | none | missing | implement |
| `hot_news_load_skills` … `hot_news_validate_images` | 热点 AI | text/image | missing | implement |

Legacy alias map (Python `HOT_NEWS_LEGACY_NODE_KEY_MAP`) accepted by catalog resolver.

## Execution semantics

| Mode | Python | Java（当前） |
|------|--------|-------------|
| formal | graph or scene pipeline with commits | Job 驱动 `GraphRuntime`；提交节点落库。产品侧已移除 dry-run / sandbox 测试运行。 |

## Persistence gaps (before → after)

| Store | Before | After |
|-------|--------|-------|
| `workflow_node_runs` | unused | written per attempt |
| `workflow_artifacts` | missing table | V7 table |
| `model_calls` | unused | written for text/image nodes |
| `workflow_templates` | in-memory | V7 DB |
| `workflow_alerts` | always `[]` | V7 DB + resolve |
| metrics nodes/versions | empty | aggregate from runs/node_runs/model_calls |

## Intentionally out of scope

- Arbitrary user script / eval nodes
- Multi-worker optimistic concurrency on one run beyond job lease
- `workflow_audit_logs` / `workflow_version_nodes` normalized tables (snapshot JSON is enough)
- FE redesign

## Test mapping

| Python / FE expectation | Java IT |
|-------------------------|---------|
| Management CRUD | `WorkflowPersistenceIT`, `WorkflowApiIT` |
| Graph condition + loop + node_runs | `WorkflowGraphE2EIT` |
| Templates + alerts | `WorkflowTemplatesAlertsIT` |
