# Cutover DoD checklist

## Preconditions
- [x] Backup branch `backup/main-20260907-before-java-rewrite`
- [x] Contracts frozen in `contracts/api-sse-contract.md`
- [x] MySQL Flyway baseline `V1__baseline.sql`
- [x] Knowledge rules copied to `knowledge/`
- [x] Python `backend/` removed from main tree

## Runtime (verified 2026-09-07)
1. `docker compose up -d` → MySQL `:3306` + Redis `:6379`
2. `mvn -pl workbench-app -am -DskipTests package && java -jar workbench-app/target/workbench-app-0.1.0-SNAPSHOT.jar`
3. Frontend: `npm run dev:frontend` (proxies `/api` → `8001`)

## Acceptance (verified)
- [x] `GET /api/health` → service/version/environment
- [x] `GET /api/settings/ai` → text/image configs (keys masked)
- [x] Hot-news channel upsert + `POST /api/hot-news/fetch` → job succeeded, fetched RSS items
- [x] Job event history with progress/succeeded frames
- [x] All registered job types have handlers and complete (course/hot-news/image/repair stubs + real fetch)
- [x] Publishing render + audit-text + courses/skills/workflows/notifications endpoints respond
- [x] ArchUnit module rules pass
- [x] Job golden-path ITs (`JobClaimRecoverIT` + `JobWorkerGoldenPathIT`) — run with:
  `mvn -pl workbench-app -am test "-Dtest=JobClaimRecoverIT,JobWorkerGoldenPathIT" "-Dsurefire.failIfNoSpecifiedTests=false"`
  (Testcontainers when Docker API works; otherwise host compose MySQL/Redis)
- [x] Hot-news fetch thin E2E (`HotNewsFetchE2EIT`) — WireMock RSS → channel test → fetch job → items persisted
  `mvn -pl workbench-app -am test "-Dtest=HotNewsFetchE2EIT" "-Dsurefire.failIfNoSpecifiedTests=false"`
- [x] Course / audit thin E2E (`CoursePipelineE2EIT` + `AuditPipelineE2EIT`)
  `mvn -pl workbench-app -am test "-Dtest=CoursePipelineE2EIT,AuditPipelineE2EIT" "-Dsurefire.failIfNoSpecifiedTests=false"`
- [x] P3 slice: hot-article rule engine + course deterministic audit (+ migrated unit tests)
  `mvn -pl workbench-publishing-audit,workbench-app -am test "-Dtest=HotArticleReviewEngineTest,CourseAuditEngineTest,AuditPipelineE2EIT,..." "-Dsurefire.failIfNoSpecifiedTests=false"`
- [x] P3 slice: hot-news item dedupe + topic aggregation (`TopicMathUtil` / `V2__hot_news_topics` / `GET /hot-news/topics`)
  `mvn -pl workbench-hot-news,workbench-app -am test "-Dtest=TopicMathUtilTest,HotNewsFetchE2EIT" "-Dsurefire.failIfNoSpecifiedTests=false"`
- [x] P3 slice: knowledge_rules + fuller topic `_score_cluster` scoring
  `mvn -pl workbench-publishing-audit,workbench-hot-news,workbench-app -am test "-Dtest=KnowledgeRulesEngineTest,TopicMathUtilTest,AuditPipelineE2EIT,HotNewsFetchE2EIT" "-Dsurefire.failIfNoSpecifiedTests=false"`

## Full Package (landed 2026-09-07)
- [x] Course content generation pipeline persists draft/wechat (`CoursePipelineE2EIT` + `GenerationPipelineE2EIT`)
- [x] Hot-news brief → generate-article → article content (`GenerationPipelineE2EIT`)
- [x] Hot-article review HTTP APIs (`POST /hot-article-reviews`, latest, repair/repair-all, accept/reject, decision) + registry
- [x] FE stubs: `PATCH /courses/{id}/lifecycle`, `GET /course-generation-jobs`, publishing themes CRUD, workflow designer routes
- [x] Smoke: `WorkflowApiIT` (`GET /workflows`, `/modes`, `/publishing/themes`)
- [x] Generation / review suite command:
  `mvn -pl workbench-publishing-audit,workbench-hot-news,workbench-content,workbench-app -am test "-Dtest=KnowledgeRulesEngineTest,TopicMathUtilTest,HotArticleReviewEngineTest,CourseAuditEngineTest,JobClaimRecoverIT,JobWorkerGoldenPathIT,HotNewsFetchE2EIT,CoursePipelineE2EIT,AuditPipelineE2EIT,GenerationPipelineE2EIT" "-Dsurefire.failIfNoSpecifiedTests=false"`

## Next Batch Depth (landed 2026-09-07)
- [x] Flyway `V4__workflow_repair` — `workflow_versions.version` VARCHAR(24) + `repair_runs`
- [x] Workflow MySQL persistence (`WorkflowFacadeImpl` + entities/mappers + builtin seeder) — `WorkflowPersistenceIT`
- [x] Image generation persist + HTTP (`POST/GET /articles/{id}/images`, `GET/DELETE /images/{id}`) — `ImageGenerationE2EIT`
- [x] Article repair pipeline + `POST /articles/{id}/repair` (+ repair-metadata) — `ArticleRepairE2EIT`
- [x] Topic research DDG search (`SearchFacade`) + fetch extract — `TopicResearchE2EIT`
- [x] Verify:
  `mvn -pl workbench-crawler,workbench-skill-workflow,workbench-content,workbench-hot-news,workbench-publishing-audit,workbench-app -am test "-Dtest=WorkflowPersistenceIT,ImageGenerationE2EIT,ArticleRepairE2EIT,TopicResearchE2EIT,WorkflowApiIT,GenerationPipelineE2EIT,HotNewsFetchE2EIT,CoursePipelineE2EIT,AuditPipelineE2EIT,JobClaimRecoverIT,JobWorkerGoldenPathIT,..." "-Dsurefire.failIfNoSpecifiedTests=false"`

## Remaining Parity (landed 2026-09-08)
- [x] Flyway `V5__remaining_parity` — layouts, course gen prefs, hot review runs/items/candidates
- [x] Versions-on-write, course generation settings DB, notifications write path (`NotificationPersistenceIT`)
- [x] Course workspace APIs: human-review / layout / publishing-snapshots / skills nodes / migrations (`CourseWorkspaceApiIT`)
- [x] Hot-news edit surface: channel patch/delete, contents, generation-runs, versions, review (`HotNewsWorkspaceApiIT`)
- [x] DB-backed hot-article repair candidates accept/reject (`HotArticleRepairCandidateIT`)
- [x] Sectioned course generation; generation fixture fallback removed (AI failure fails the job)
- [x] Workflow listRuns + metrics（产品侧 dry-run / sandbox 测试运行已移除；正式生成走 GraphRuntime）
- [x] Verify:
  `mvn -pl workbench-crawler,workbench-skill-workflow,workbench-content,workbench-hot-news,workbench-publishing-audit,workbench-app -am test "-Dtest=CourseWorkspaceApiIT,HotNewsWorkspaceApiIT,NotificationPersistenceIT,HotArticleRepairCandidateIT,WorkflowPersistenceIT,ImageGenerationE2EIT,ArticleRepairE2EIT,TopicResearchE2EIT,GenerationPipelineE2EIT,HotNewsFetchE2EIT,CoursePipelineE2EIT,AuditPipelineE2EIT,JobClaimRecoverIT,JobWorkerGoldenPathIT,WorkflowApiIT,KnowledgeRulesEngineTest,TopicMathUtilTest,HotArticleReviewEngineTest,CourseAuditEngineTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

## Remaining known gaps
- [x] Workflow draft/publish/versions designer stubs (in-memory; list remains `[]` until create)
- [x] Publishing themes CRUD (+ auto-seed default theme so list is never empty)
- [x] Hot-article accept returns `HOT_ARTICLE_REVIEW_REVALIDATE` JobData JSON
- [x] Course-generation-jobs lists `jobs` via `JobFacade.listByType(course_content_generation)`
- [x] `GET /articles/{id}/versions` → `[]` (mapper optional later)
- [x] Image generation / article repair still thin / dry-run oriented → **deepened** (fixture/AI + disk + repair_runs)
- [x] Workflow persistence to `workflow_definitions` / `_versions` / `_drafts` (in-memory stubs today) → **MySQL**
- [x] Image/repair production hardening (upstream-only, no fixture fallback in prod profile) → `application-prod.yml`
- [x] `workflow_runs` listing + true graph execution（dry-run/sandbox 产品入口已移除）
- [x] Workflow **true graph execution** engine + node catalog + `workflow_node_runs` / artifacts (`WorkflowGraphE2EIT`)
- [x] Workflow templates / alerts DB + metrics aggregation (`WorkflowTemplatesAlertsIT`, V7)
- [x] PG→MySQL runbook + verify scripts ready ([`docs/cutover-pg-to-mysql.md`](cutover-pg-to-mysql.md), [`scripts/pg-mysql-cutover/`](../scripts/pg-mysql-cutover/))
- [x] PG→MySQL **live data migration** completed 2026-09-11 via DataX (`verify_counts` + `sample_ids` PASSED)

## Data migration (live PG → MySQL)
Executed 2026-09-11 stop-window with DataX. See [`docs/cutover-pg-to-mysql.md`](cutover-pg-to-mysql.md) § DataX. Not required for empty greenfield MySQL.
