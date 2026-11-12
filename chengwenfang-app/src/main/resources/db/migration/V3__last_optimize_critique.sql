-- 热点原稿优化诊断结果落库，供工作区回填
ALTER TABLE `work_generation_settings`
  ADD COLUMN `last_optimize_critique` mediumtext COLLATE utf8mb4_unicode_ci
    COMMENT '热点：最近一次优化诊断 JSON' AFTER `rewritten_at`;
