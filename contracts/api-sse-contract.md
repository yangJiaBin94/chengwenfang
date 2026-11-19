# Frozen API / SSE contract (Python → Java cutover)

Source freeze commit: `backup/main-20260907-before-java-rewrite` (`f04f797`).

## Base

- Prefix: `/api`
- App port: `8001`
- Vite proxy: `/api` → `http://127.0.0.1:8001`

## Health

`GET /api/health` → `{ "service", "version", "environment" }`

## AI settings

- `GET /api/settings/ai`
- `PUT /api/settings/ai`
- `POST /api/settings/ai/{kind}/test` (`kind` = `image` | `vision` | `embedding`)
- `POST /api/ai/vision`（多模态补全，图片可选）
- `POST /api/ai/image`

API keys: responses never return plaintext; empty key on PUT keeps stored value.

## Jobs

- `GET /api/jobs/{jobId}`
- `GET /api/jobs/{jobId}/result`
- `POST /api/jobs/{jobId}/cancel`
- `POST /api/jobs/{jobId}/retry-failed` → 202
- `GET /api/jobs/{jobId}/events` — SSE
- `GET /api/jobs/{jobId}/event-history`
- `GET /api/jobs/{jobId}/nodes`
- `GET /api/jobs/{jobId}/article-versions`
- `GET /api/workflow-runs/{workflowRunId}/nodes`

### SSE (`GET /api/jobs/{jobId}/events`)

- Resume: header `Last-Event-ID` (int) or query `?after=`
- Frame:

```
id: {event.id}
event: {event.eventType}
data: {"id","eventType","step","progress","message","payload","createdAt"}
```

- Poll interval: `CHENGWENFANG_SSE_POLL_SECONDS` (default 0.5)
- Close when job terminal and no pending events

### JobResponse

`id, type, status, entityType, entityId, progress, currentStep, workflowRunId, output, errorCode, errorMessage, retryCount, createdAt, startedAt, finishedAt`

## Supported job types

`hot_news_fetch`（雷达采集 FormalGraph：`hot_news_radar_collect` scene，节点进度经 `workflow_node_runs` / `/api/jobs/{id}/nodes`）, `course_content_generation`, `hot_news_article_generation`, `hot_news_article_rewrite`, `hot_news_topic_research`, `hot_news_video_parse`, `hot_news_source_video_parse`, `hot_article_review`, `hot_article_review_repair`, `hot_article_review_revalidate`, `course_draft_generation`, `wechat_article_generation`, `image_generation`, `article_repair`, `theme_curriculum_generation`, `knowledge_index_document`（终态**不**推站内通知）, `knowledge_reindex_all`, `knowledge_reconcile`

## Knowledge (`/api/knowledge`)

依赖本机 Qdrant（默认 `127.0.0.1:6333`）与设置页嵌入模型；设计见 `docs/知识库RAG体系设计.md`。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/documents/register` | 登记已有来源正文 |
| POST | `/documents/upload` | multipart 上传入库 |
| POST | `/documents/ingest-url` | 网页拉取入库 |
| GET | `/documents` | 文档列表 |
| POST | `/documents/{id}/reindex` | 单文档重建索引 Job |
| POST | `/reindex` | 全量 / 按来源重建 |
| POST | `/reconcile` | MySQL ↔ 向量 / 图对账 Job |
| POST | `/reconcile/preview` | 对账预览 |
| GET | `/stats` | 库统计 |
| GET | `/index-version` | 活跃索引版本 |
| POST | `/index-version/promote` | 提升读指针版本 |
| POST | `/graph/edges/correct` | 图边纠错（需 Graph 开启） |
| POST | `/chats` | 新建会话 |
| GET | `/chats` | 会话列表 |
| PATCH / DELETE | `/chats/{id}` | 改名 / 删除 |
| GET | `/chats/{id}/messages` | 历史消息 |
| GET | `/chats/{id}/traces` | 检索 / 生成轨迹 |
| POST | `/chats/{id}/messages` | **SSE** 问答流（`TEXT_EVENT_STREAM`） |
| POST | `/messages/{messageId}/feedback` | 消息反馈 |
| GET | `/memories` | 长期记忆列表 |

## Other route groups (parity targets)

`/api/notifications`, `/api/settings/prompt-packs`, `/api/workflows`, hot-news, content, publishing, images, generation, audit, repair, human-reviews, content-context, knowledge
