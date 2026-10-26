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
- `POST /api/settings/ai/{kind}/test` (`kind` = `text` | `image`)
- `POST /api/ai/text`
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

`hot_news_fetch`（雷达采集 FormalGraph：`hot_news_radar_collect` scene，节点进度经 `workflow_node_runs` / `/api/jobs/{id}/nodes`）, `course_content_generation`, `hot_news_article_generation`, `hot_news_article_rewrite`, `hot_news_topic_research`, `hot_article_review`, `hot_article_review_repair`, `hot_article_review_revalidate`, `course_draft_generation`, `wechat_article_generation`, `image_generation`, `article_repair`

## Other route groups (parity targets)

`/api/notifications`, `/api/settings/skills`, `/api/workflows`, hot-news, content, publishing, images, generation, audit, repair, human-reviews, content-context
