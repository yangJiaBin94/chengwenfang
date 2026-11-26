-- U4：sourcing / course / hotnews / quality / publishing / workflow / 平台余表
-- 历史回填种子管理员；workflow_key / layout theme_key / notification dedupe 改为按用户唯一

SET @admin := '00000000-0000-4000-8000-000000000001';

-- ---------- prompt / workflow ----------
ALTER TABLE `prompt_packs`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `workflow_definitions`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `workflow_drafts`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `workflow_runs`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `workflow_alerts`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

-- ---------- platform leftovers ----------
ALTER TABLE `model_calls`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `notifications`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

-- ---------- sourcing ----------
ALTER TABLE `source_channels`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `source_items`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `source_fetch_runs`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `source_query_site_blocks`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `video_transcripts`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

-- ---------- course ----------
ALTER TABLE `course_curricula`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `theme_id`;
ALTER TABLE `course_series`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

-- ---------- hotnews ----------
ALTER TABLE `hotnews_radar_batches`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `hotnews_topics`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `hotnews_briefs`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `hotnews_works`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `work_id`;
ALTER TABLE `hotnews_research_runs`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `hotnews_article_skeletons`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `topic_id`;

-- ---------- quality ----------
ALTER TABLE `audit_runs`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `human_reviews`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `repair_runs`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

-- ---------- publishing（channel_accounts 已在 U1） ----------
ALTER TABLE `layout_themes`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `publications`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

-- 回填
UPDATE `prompt_packs` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `workflow_definitions` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `workflow_drafts` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `workflow_runs` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `workflow_alerts` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `model_calls` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `notifications` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `source_channels` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `source_items` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `source_fetch_runs` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `source_query_site_blocks` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `video_transcripts` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `course_curricula` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `course_series` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `hotnews_radar_batches` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `hotnews_topics` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `hotnews_briefs` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `hotnews_works` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `hotnews_research_runs` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `hotnews_article_skeletons` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `audit_runs` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `human_reviews` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `repair_runs` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `layout_themes` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `publications` SET `owner_user_id` = @admin WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';

-- NOT NULL + 索引；跨用户唯一键收窄
ALTER TABLE `prompt_packs`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_prompt_packs_owner` (`owner_user_id`);

ALTER TABLE `workflow_definitions` DROP INDEX `key`;
ALTER TABLE `workflow_definitions`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD UNIQUE KEY `uk_workflow_definitions_owner_key` (`owner_user_id`, `workflow_key`),
  ADD KEY `idx_workflow_definitions_owner` (`owner_user_id`);

ALTER TABLE `workflow_drafts`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_workflow_drafts_owner` (`owner_user_id`);

ALTER TABLE `workflow_runs`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_workflow_runs_owner` (`owner_user_id`);

ALTER TABLE `workflow_alerts`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_workflow_alerts_owner` (`owner_user_id`);

ALTER TABLE `model_calls`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_model_calls_owner` (`owner_user_id`);

ALTER TABLE `notifications` DROP INDEX `uk_notifications_dedupe`;
ALTER TABLE `notifications`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD UNIQUE KEY `uk_notifications_owner_dedupe` (`owner_user_id`, `dedupe_key`),
  ADD KEY `idx_notifications_owner` (`owner_user_id`);

ALTER TABLE `source_channels`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_source_channels_owner` (`owner_user_id`);

ALTER TABLE `source_items`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_source_items_owner` (`owner_user_id`);

ALTER TABLE `source_fetch_runs`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_source_fetch_runs_owner` (`owner_user_id`);

ALTER TABLE `source_query_site_blocks`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_source_query_site_blocks_owner` (`owner_user_id`);

ALTER TABLE `video_transcripts`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_video_transcripts_owner` (`owner_user_id`);

ALTER TABLE `course_curricula`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_course_curricula_owner` (`owner_user_id`);

ALTER TABLE `course_series`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_course_series_owner` (`owner_user_id`);

ALTER TABLE `hotnews_radar_batches`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_hotnews_radar_batches_owner` (`owner_user_id`);

ALTER TABLE `hotnews_topics`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_hotnews_topics_owner` (`owner_user_id`);

ALTER TABLE `hotnews_briefs`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_hotnews_briefs_owner` (`owner_user_id`);

ALTER TABLE `hotnews_works`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_hotnews_works_owner` (`owner_user_id`);

ALTER TABLE `hotnews_research_runs`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_hotnews_research_runs_owner` (`owner_user_id`);

ALTER TABLE `hotnews_article_skeletons`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_hotnews_article_skeletons_owner` (`owner_user_id`);

ALTER TABLE `audit_runs`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_audit_runs_owner` (`owner_user_id`);

ALTER TABLE `human_reviews`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_human_reviews_owner` (`owner_user_id`);

ALTER TABLE `repair_runs`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_repair_runs_owner` (`owner_user_id`);

ALTER TABLE `layout_themes` DROP INDEX `uk_layout_themes_key`;
ALTER TABLE `layout_themes`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD UNIQUE KEY `uk_layout_themes_owner_key` (`owner_user_id`, `theme_key`),
  ADD KEY `idx_layout_themes_owner` (`owner_user_id`);

ALTER TABLE `publications`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_publications_owner` (`owner_user_id`);
