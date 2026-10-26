-- P7.1：全新 schema（合并原 V1–V63 终态）
-- 校对：docs/refactor-v2/02-新表结构设计.md 章节顺序；字符集 utf8mb4_unicode_ci（D-18）
-- 字典种子见 V2__dictionaries.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 4.1 字典
-- ---------------------------------------------------------------------------

CREATE TABLE `content_modes` (
  `code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模式编码：course_system / hot_news',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名称',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled / disabled',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容模式字典';

CREATE TABLE `channels` (
  `code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道编码：master（原稿）/ wechat_mp（公众号），以后 x / bilibili / xiaohongshu / douyin',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名称',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发布渠道字典';

CREATE TABLE `output_formats` (
  `code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产出格式编码：article，以后 image_post / thread / video_script / video',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名称',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产出格式字典';

CREATE TABLE `channel_formats` (
  `channel_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道编码',
  `format_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '格式编码',
  PRIMARY KEY (`channel_code`,`format_code`),
  KEY `fk_channel_formats_format` (`format_code`),
  CONSTRAINT `fk_channel_formats_channel` FOREIGN KEY (`channel_code`) REFERENCES `channels` (`code`),
  CONSTRAINT `fk_channel_formats_format` FOREIGN KEY (`format_code`) REFERENCES `output_formats` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道支持的产出格式';

-- ---------------------------------------------------------------------------
-- 4.2 文件与运行时（平台层）
-- ---------------------------------------------------------------------------

CREATE TABLE `stored_files` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `storage_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储类型：LOCAL / QINIU / MINIO',
  `bucket_or_root` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '云端 bucket；本地可空',
  `object_key` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '相对路径或 object key',
  `file_name` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示文件名',
  `content_type` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MIME 类型',
  `size_bytes` bigint DEFAULT NULL COMMENT '字节大小',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容哈希',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_stored_files_type` (`storage_type`),
  KEY `idx_stored_files_object_key` (`object_key`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一文件登记（真实路径 / object key 只存本表）';

CREATE TABLE `jobs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `job_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务类型（JobTypes 原值不变）',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：pending / queued / running / succeeded / failed / cancelled',
  `entity_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务目标实体类型（JobEntityTypes 原值不变）',
  `entity_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务目标实体 ID',
  `theme_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属主题（统计与按主题查活动任务用）',
  `work_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属作品（统计与排查用）',
  `progress` int NOT NULL DEFAULT '0' COMMENT '进度 0–100',
  `current_step` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '当前步骤名',
  `input_json` json DEFAULT NULL COMMENT '入参快照（调试用，禁止用于查询）',
  `output_json` json DEFAULT NULL COMMENT '出参快照（调试用，禁止用于查询）',
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误码',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `max_retries` int NOT NULL DEFAULT '0' COMMENT '最大重试次数',
  `lease_expires_at` datetime(3) DEFAULT NULL COMMENT 'Worker 租约过期时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `started_at` datetime(3) DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_jobs_status_lease` (`status`,`lease_expires_at`),
  KEY `idx_jobs_entity` (`entity_type`,`entity_id`),
  KEY `idx_jobs_status_created` (`status`,`created_at`),
  KEY `idx_jobs_theme_status` (`theme_id`,`status`),
  KEY `idx_jobs_work` (`work_id`),
  KEY `idx_jobs_type_created` (`job_type`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步任务';

CREATE TABLE `job_events` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联异步任务 ID',
  `event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型：queued / progress / succeeded 等',
  `step` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '步骤名',
  `progress` int NOT NULL DEFAULT '0' COMMENT '进度 0–100',
  `message` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '消息正文',
  `payload_json` json DEFAULT NULL COMMENT '事件附加载荷',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_events_job_id` (`job_id`,`id`),
  CONSTRAINT `fk_job_events_job` FOREIGN KEY (`job_id`) REFERENCES `jobs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务事件（SSE 增量）';

CREATE TABLE `ai_provider_configs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类别：text / image / vision',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名称',
  `model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名',
  `base_url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上游 API 根地址',
  `api_key_ciphertext` text COLLATE utf8mb4_unicode_ci COMMENT '密钥密文',
  `protocol` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'openai_chat' COMMENT '协议',
  `timeout_seconds` int NOT NULL DEFAULT '60' COMMENT '超时秒数',
  `max_retries` int NOT NULL DEFAULT '2' COMMENT '上游最大重试次数',
  `temperature` double DEFAULT NULL COMMENT '采样温度',
  `max_output_tokens` int DEFAULT NULL COMMENT '最大输出 token',
  `image_quality` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图片质量档',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `kind` (`kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 上游配置';

CREATE TABLE `model_calls` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `job_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属任务',
  `work_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属作品',
  `workflow_run_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作流运行 ID',
  `workflow_node_run_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点运行 ID',
  `provider_config_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI 配置 ID',
  `scene` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务场景',
  `model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名',
  `protocol` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '协议',
  `prompt_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提示词版本',
  `input_chars` int DEFAULT NULL COMMENT '输入字符数',
  `output_chars` int DEFAULT NULL COMMENT '输出字符数',
  `input_tokens` int DEFAULT NULL COMMENT '输入 token',
  `output_tokens` int DEFAULT NULL COMMENT '输出 token',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态',
  `upstream_status` int DEFAULT NULL COMMENT '上游 HTTP 状态码',
  `upstream_request_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上游请求 ID',
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误码',
  `response_mode` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '响应模式',
  `exception_type` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '异常类型',
  `finish_reason` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结束原因',
  `first_byte_ms` bigint DEFAULT NULL COMMENT '首字节耗时',
  `response_event_count` int DEFAULT NULL COMMENT '流式事件条数',
  `fallback_used` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否降级',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_model_calls_scene` (`scene`,`created_at`),
  KEY `idx_model_calls_job` (`job_id`),
  KEY `idx_model_calls_work` (`work_id`),
  KEY `idx_model_calls_run` (`workflow_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型调用审计';

CREATE TABLE `notifications` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `notification_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知类型（NotificationTypes 原值）',
  `level` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '级别：success / error（原值）',
  `title` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `message` text COLLATE utf8mb4_unicode_ci COMMENT '正文',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'unread' COMMENT '状态：unread / read',
  `read_at` datetime(3) DEFAULT NULL COMMENT '已读时间',
  `dedupe_key` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '去重键：jobType:jobId:status',
  `job_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联任务',
  `job_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务类型',
  `workflow_run_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作流运行 ID',
  `mode_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模式编码',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主题 ID',
  `theme_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主题编码快照（拼路由用）',
  `theme_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主题名称快照',
  `work_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作品 ID（原 courseId / 热点 articleId）',
  `output_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产出 ID（原 documentId，公众号轨）',
  `draft_output_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原稿产出 ID（原 draftDocumentId）',
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '热点选题 ID',
  `fetch_run_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '抓取运行 ID',
  `research_run_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '调研运行 ID',
  `subject_title` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主体标题快照（原 courseTitle / articleTitle）',
  `article_title` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '热点文章标题快照（原 articleTitle）',
  `series_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程系列名快照',
  `lesson_no` int DEFAULT NULL COMMENT '课序号快照',
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败错误码',
  `raw_error_message` text COLLATE utf8mb4_unicode_ci COMMENT '失败原始信息',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notifications_dedupe` (`dedupe_key`),
  KEY `idx_notifications_status_created` (`status`,`created_at`),
  KEY `idx_notifications_job` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知（跳转与展示字段全部结构化）';

CREATE TABLE `skills` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '描述',
  `scope` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作用域',
  `skill_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Skill 类型',
  `locked` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否锁定不可随意改',
  `scene` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '场景',
  `system_instructions` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '系统提示词',
  `task_instructions` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '任务提示词',
  `priority` int NOT NULL DEFAULT '100' COMMENT '优先级（数值越小越优先，默认 100）',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '软删除时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill（提示词与能力定义）';

CREATE TABLE `skill_default_bindings` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `skill_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Skill ID',
  `workflow_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流类型',
  `node_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点键',
  `priority` int NOT NULL DEFAULT '100' COMMENT '优先级',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `fk_skill_bindings_skill` (`skill_id`),
  KEY `idx_skill_default_bindings_node` (`workflow_type`,`node_key`),
  CONSTRAINT `fk_skill_bindings_skill` FOREIGN KEY (`skill_id`) REFERENCES `skills` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill 与工作流节点的全局默认绑定';

CREATE TABLE `workflow_definitions` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `workflow_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流稳定键（原 key）',
  `name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '描述',
  `scene` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '场景',
  `mode_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属模式（原 mode_id）',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态',
  `published_version_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前已发布版本',
  `owner` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `key` (`workflow_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义';

CREATE TABLE `workflow_versions` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `workflow_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流定义 ID',
  `version` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号字符串',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号',
  `schema_version` int NOT NULL DEFAULT '1' COMMENT '图定义格式版本',
  `snapshot_json` json NOT NULL COMMENT '图定义（配置文档）',
  `validation_json` json DEFAULT NULL COMMENT '校验结果（调试快照）',
  `release_notes` text COLLATE utf8mb4_unicode_ci COMMENT '发布说明',
  `published_by` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_version` (`workflow_id`,`version`),
  CONSTRAINT `fk_workflow_versions_def` FOREIGN KEY (`workflow_id`) REFERENCES `workflow_definitions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流已发布版本';

CREATE TABLE `workflow_drafts` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `workflow_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流定义 ID',
  `base_version_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '基于的已发布版本',
  `revision` int NOT NULL DEFAULT '1' COMMENT '修订号',
  `schema_version` int NOT NULL DEFAULT '1' COMMENT '图定义格式版本',
  `definition_json` json NOT NULL COMMENT '图定义草稿（配置文档）',
  `validation_json` json DEFAULT NULL COMMENT '校验结果（调试快照）',
  `updated_by` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `workflow_id` (`workflow_id`),
  CONSTRAINT `fk_workflow_drafts_def` FOREIGN KEY (`workflow_id`) REFERENCES `workflow_definitions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流草稿';

CREATE TABLE `workflow_templates` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `template_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板稳定键（原 key）',
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '描述',
  `scene` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '场景',
  `official` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否官方模板',
  `schema_version` int NOT NULL DEFAULT '1' COMMENT '图定义格式版本',
  `snapshot_json` json NOT NULL COMMENT '模板图定义（配置文档）',
  `source_workflow_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源工作流',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_templates_key` (`template_key`),
  KEY `ix_workflow_templates_source` (`source_workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流模板';

CREATE TABLE `workflow_version_skill_bindings` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `version_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本 ID',
  `node_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点键',
  `skill_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Skill ID',
  `skill_version` int DEFAULT NULL COMMENT 'Skill 版本号',
  `priority` int NOT NULL DEFAULT '100' COMMENT '优先级（数值越小越优先，默认 100）',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容哈希',
  `snapshot_json` json DEFAULT NULL COMMENT '快照 JSON',
  PRIMARY KEY (`id`),
  KEY `idx_wvsb_version_node` (`version_id`,`node_key`),
  CONSTRAINT `fk_wvsb_version` FOREIGN KEY (`version_id`) REFERENCES `workflow_versions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流版本的节点 Skill 绑定快照';

CREATE TABLE `workflow_runs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `job_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属任务',
  `workflow_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流名称',
  `thread_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图运行线程 ID',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：queued / running / succeeded / failed',
  `current_node` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前节点',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '失败信息（原 state_summary.error）',
  `workflow_definition_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作流定义 ID',
  `workflow_version_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作流版本 ID',
  `parent_run_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父运行 ID',
  `trigger_source` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发来源',
  `snapshot_json` json DEFAULT NULL COMMENT '运行时图定义快照（配置文档）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `job_id` (`job_id`),
  CONSTRAINT `fk_workflow_runs_job` FOREIGN KEY (`job_id`) REFERENCES `jobs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流运行';

CREATE TABLE `workflow_run_executed_nodes` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `workflow_run_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流运行 ID',
  `seq` int NOT NULL COMMENT '执行顺序',
  `node_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '已执行节点键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wren_run_seq` (`workflow_run_id`,`seq`),
  CONSTRAINT `fk_wren_run` FOREIGN KEY (`workflow_run_id`) REFERENCES `workflow_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流运行已执行节点序列（原 state_summary.executed）';

CREATE TABLE `workflow_node_runs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `workflow_run_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流运行 ID',
  `node_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点键',
  `sequence_no` int NOT NULL DEFAULT '0' COMMENT '序号',
  `attempt_no` int NOT NULL DEFAULT '1' COMMENT '尝试次数',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态',
  `input_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '输入哈希',
  `output_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '输出哈希',
  `input_summary` json DEFAULT NULL COMMENT '输入摘要 JSON',
  `output_summary` json DEFAULT NULL COMMENT '输出摘要 JSON',
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误码',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `executor_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行器版本',
  `config_snapshot` json DEFAULT NULL COMMENT '配置快照 JSON',
  `parent_node_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父节点键',
  `heartbeat_at` datetime(3) DEFAULT NULL COMMENT '节点心跳时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  `started_at` datetime(3) DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '结束时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_node_runs_run` (`workflow_run_id`,`sequence_no`),
  CONSTRAINT `fk_node_runs_run` FOREIGN KEY (`workflow_run_id`) REFERENCES `workflow_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点运行';

CREATE TABLE `workflow_artifacts` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `workflow_run_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流运行 ID',
  `node_run_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点运行 ID',
  `artifact_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产物类型',
  `content_format` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'text' COMMENT '内容格式：text / json',
  `content_file_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产物内容文件',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容哈希',
  `schema_version` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模式版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `ix_workflow_artifacts_run` (`workflow_run_id`),
  KEY `fk_workflow_artifacts_file` (`content_file_id`),
  CONSTRAINT `fk_workflow_artifacts_file` FOREIGN KEY (`content_file_id`) REFERENCES `stored_files` (`id`),
  CONSTRAINT `fk_workflow_artifacts_run` FOREIGN KEY (`workflow_run_id`) REFERENCES `workflow_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流产物（内容存文件）';

CREATE TABLE `workflow_alerts` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `workflow_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流定义 ID',
  `run_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联运行 ID',
  `metric` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '指标名',
  `severity` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'warning' COMMENT '严重级别',
  `title` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `message` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'open' COMMENT '状态',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `resolved_at` datetime(3) DEFAULT NULL COMMENT '解决时间',
  PRIMARY KEY (`id`),
  KEY `ix_workflow_alerts_workflow` (`workflow_id`),
  KEY `ix_workflow_alerts_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流告警';

-- ---------------------------------------------------------------------------
-- 4.3 主题层
-- ---------------------------------------------------------------------------

CREATE TABLE `themes` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `mode_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属模式',
  `code` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题编码（路由 slug 来源）',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题名称',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'draft' COMMENT '状态：draft / ready_for_review / enabled / disabled',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `created_via` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'wizard' COMMENT '创建途径',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '软删除时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_themes_mode_code` (`mode_code`,`code`),
  KEY `ix_content_themes_status` (`status`),
  KEY `ix_content_themes_deleted_at` (`deleted_at`),
  KEY `idx_themes_mode_status` (`mode_code`,`status`,`sort_order`),
  CONSTRAINT `fk_themes_mode` FOREIGN KEY (`mode_code`) REFERENCES `content_modes` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容主题';

CREATE TABLE `theme_profiles` (
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `profile_kind` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '画像种类：briefing / defaults',
  `schema_version` int NOT NULL DEFAULT '1' COMMENT '原 schema_version 值',
  `wizard_step` int DEFAULT NULL COMMENT '向导步骤 step',
  `naming_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'naming.name',
  `naming_description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'naming.description',
  `naming_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'naming.code',
  `domain` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'naming.domain / defaults.domain',
  `domain_custom` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'naming.domain_custom',
  `content_stance` text COLLATE utf8mb4_unicode_ci COMMENT 'audience.content_stance',
  `readers_note` text COLLATE utf8mb4_unicode_ci COMMENT 'audience.readers_note',
  `time_budget` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'audience.time_budget',
  `success_criteria` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'goals.success_criteria',
  `difficulty_curve` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'scope.difficulty_curve',
  `series_count_pref` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'structure.series_count_pref',
  `series_count_custom` int DEFAULT NULL COMMENT 'structure.series_count_custom',
  `lessons_per_series_pref` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'structure.lessons_per_series_pref',
  `lessons_per_series_custom` int DEFAULT NULL COMMENT 'structure.lessons_per_series_custom',
  `need_overview_lessons` tinyint(1) DEFAULT NULL COMMENT 'structure.need_overview_lessons',
  `out_of_scope` text COLLATE utf8mb4_unicode_ci COMMENT 'scope.out_of_scope（热点 string；课程优先 items）',
  `locale` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'scope.locale',
  `github_query` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'radar.github_query',
  `tone` text COLLATE utf8mb4_unicode_ci COMMENT 'expression.tone',
  `length_preference` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'expression.length / defaults.length',
  `boundaries` text COLLATE utf8mb4_unicode_ci COMMENT 'expression.boundaries',
  `lesson_forms` text COLLATE utf8mb4_unicode_ci COMMENT 'structure.lesson_forms / defaults.lessonForms（JSON 数组文本）',
  `word_count_option` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'defaults.wordCountOption（课程）',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`theme_id`,`profile_kind`),
  CONSTRAINT `fk_theme_profiles_theme` FOREIGN KEY (`theme_id`) REFERENCES `themes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_theme_profiles_kind` CHECK ((`profile_kind` in (_utf8mb4'briefing',_utf8mb4'defaults')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主题画像（向导问卷与默认偏好）';

CREATE TABLE `theme_profile_items` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `profile_kind` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '画像种类',
  `item_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表类型：reader / prerequisite / outcome / must_cover / must_watch / worth_writing / search_query / seed_channel / out_of_scope',
  `seq` int NOT NULL COMMENT '列表内顺序',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表项内容',
  PRIMARY KEY (`id`),
  KEY `idx_theme_profile_items` (`theme_id`,`profile_kind`,`item_type`,`seq`),
  CONSTRAINT `fk_theme_profile_items_profile` FOREIGN KEY (`theme_id`, `profile_kind`) REFERENCES `theme_profiles` (`theme_id`, `profile_kind`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主题画像中的列表字段';

CREATE TABLE `theme_scoring_rubrics` (
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `profile_kind` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '画像种类',
  `rubric_version` int DEFAULT NULL COMMENT 'scoring_rubric.version',
  `generated_at` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'scoring_rubric.generatedAt（原字符串）',
  `score_guidance` text COLLATE utf8mb4_unicode_ci COMMENT 'scoring_rubric.scoreGuidance',
  PRIMARY KEY (`theme_id`,`profile_kind`),
  CONSTRAINT `fk_theme_scoring_rubrics_profile` FOREIGN KEY (`theme_id`, `profile_kind`) REFERENCES `theme_profiles` (`theme_id`, `profile_kind`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主题评分细则';

CREATE TABLE `theme_scoring_rubric_dimensions` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `profile_kind` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '画像种类',
  `seq` int NOT NULL COMMENT '顺序',
  `dimension_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维度键',
  `label` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维度名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '维度说明',
  `weight` int DEFAULT NULL COMMENT '权重',
  PRIMARY KEY (`id`),
  KEY `idx_tsrd` (`theme_id`,`profile_kind`,`seq`),
  CONSTRAINT `fk_tsrd_rubric` FOREIGN KEY (`theme_id`, `profile_kind`) REFERENCES `theme_scoring_rubrics` (`theme_id`, `profile_kind`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主题评分细则维度';

-- ---------------------------------------------------------------------------
-- 4.4 作品主线
-- ---------------------------------------------------------------------------

CREATE TABLE `works` (
  `id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键（原 courseId；热点 articleId 后续挂入）',
  `mode_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属模式',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属主题',
  `title` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作品标题',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_works_theme` (`theme_id`,`created_at`),
  KEY `idx_works_mode` (`mode_code`,`created_at`),
  CONSTRAINT `fk_works_mode` FOREIGN KEY (`mode_code`) REFERENCES `content_modes` (`code`),
  CONSTRAINT `fk_works_theme` FOREIGN KEY (`theme_id`) REFERENCES `themes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品：所有模式的生产单元';

CREATE TABLE `work_generation_settings` (
  `work_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作品 ID',
  `word_count_option` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '字数档位（原值，如 2000-4000）',
  `image_count_option` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配图档位（原值，如 3-5）',
  `recommended_word_count_option` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '系统推荐字数档位',
  `recommended_image_count_option` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '系统推荐配图档位',
  `recommendation_reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '推荐理由',
  `recommended_at` datetime(3) DEFAULT NULL COMMENT '推荐时间',
  `preference_fingerprint` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '推荐结果指纹',
  `working_title` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '热点：工作标题',
  `illustration_style` text COLLATE utf8mb4_unicode_ci COMMENT '热点：插画风格',
  `cover_brief` text COLLATE utf8mb4_unicode_ci COMMENT '热点：封面说明',
  `image_prompt_requirements` text COLLATE utf8mb4_unicode_ci COMMENT '热点：配图提示词要求',
  `last_rewrite_instruction` text COLLATE utf8mb4_unicode_ci COMMENT '热点：最近一次改写指令',
  `rewritten_at` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '热点：改写时间（原 ISO 字符串）',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`work_id`),
  CONSTRAINT `fk_wgs_work` FOREIGN KEY (`work_id`) REFERENCES `works` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品生成设置（课程字数配图偏好与热点生成参数统一于此）';

CREATE TABLE `work_outputs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键（原 documentId）',
  `work_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作品 ID',
  `channel_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道：master / wechat_mp',
  `format_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '格式：article',
  `title` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '标题',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'draft' COMMENT '状态（原 content_documents.status 取值）',
  `current_version_no` int NOT NULL DEFAULT '0' COMMENT '当前版本号',
  `word_count` int NOT NULL DEFAULT '0' COMMENT '当前正文字数',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前正文哈希',
  `version_token` bigint DEFAULT NULL COMMENT '乐观锁令牌（课程使用）',
  `workflow_run_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次生成的工作流运行（热点使用）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_outputs_work_channel_format` (`work_id`,`channel_code`,`format_code`),
  KEY `fk_work_outputs_channel` (`channel_code`),
  KEY `fk_work_outputs_format` (`format_code`),
  CONSTRAINT `fk_work_outputs_channel` FOREIGN KEY (`channel_code`) REFERENCES `channels` (`code`),
  CONSTRAINT `fk_work_outputs_format` FOREIGN KEY (`format_code`) REFERENCES `output_formats` (`code`),
  CONSTRAINT `fk_work_outputs_work` FOREIGN KEY (`work_id`) REFERENCES `works` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品在某渠道某格式下的产出（一轨一行）';

CREATE TABLE `work_output_versions` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `output_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产出 ID',
  `version_no` int NOT NULL COMMENT '版本号',
  `body_file_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '正文文件',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '正文哈希',
  `source` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本来源',
  `reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产生原因',
  `workflow_run_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联工作流运行',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wov_output_version` (`output_id`,`version_no`),
  KEY `fk_wov_file` (`body_file_id`),
  CONSTRAINT `fk_wov_file` FOREIGN KEY (`body_file_id`) REFERENCES `stored_files` (`id`),
  CONSTRAINT `fk_wov_output` FOREIGN KEY (`output_id`) REFERENCES `work_outputs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产出正文版本（正文存文件，只追加不覆盖）';

CREATE TABLE `media_assets` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `work_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作品 ID',
  `output_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产出 ID（原 document_id）',
  `origin` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源：generated / synced',
  `media_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'image' COMMENT '媒体类型',
  `usage_kind` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用途（原 kind）',
  `sequence_no` int NOT NULL DEFAULT '0' COMMENT '序号',
  `file_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容哈希',
  `width` int DEFAULT NULL COMMENT '宽度像素',
  `height` int DEFAULT NULL COMMENT '高度像素',
  `alt_text` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '替代文本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_media_assets_work` (`work_id`,`origin`,`sequence_no`),
  KEY `idx_media_assets_output` (`output_id`,`sequence_no`),
  KEY `fk_media_assets_file` (`file_id`),
  CONSTRAINT `fk_media_assets_file` FOREIGN KEY (`file_id`) REFERENCES `stored_files` (`id`),
  CONSTRAINT `fk_media_assets_work` FOREIGN KEY (`work_id`) REFERENCES `works` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_media_assets_origin` CHECK ((`origin` in (_utf8mb4'generated',_utf8mb4'synced')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品媒体资产（合并 AI 配图与课程同步媒体）';

CREATE TABLE `media_generations` (
  `asset_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '媒体资产 ID',
  `prompt` text COLLATE utf8mb4_unicode_ci COMMENT '生图提示词',
  `provider_config_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI 配置 ID',
  `model` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型名',
  `size` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '尺寸',
  `quality` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '质量档',
  `output_format` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '输出格式',
  `job_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生成任务',
  PRIMARY KEY (`asset_id`),
  CONSTRAINT `fk_media_generations_asset` FOREIGN KEY (`asset_id`) REFERENCES `media_assets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 生成媒体的生成参数';

-- ---------------------------------------------------------------------------
-- 4.5 课程模式扩展
-- ---------------------------------------------------------------------------

CREATE TABLE `course_curricula` (
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `route_narrative` longtext COLLATE utf8mb4_unicode_ci COMMENT '已提交的路线叙事',
  `draft_schema_version` int DEFAULT NULL COMMENT '课表草案 schemaVersion（无草案时为空）',
  `draft_route_narrative` longtext COLLATE utf8mb4_unicode_ci COMMENT '课表草案路线叙事',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`theme_id`),
  CONSTRAINT `fk_course_curricula_theme` FOREIGN KEY (`theme_id`) REFERENCES `themes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程主题的课表（路线叙事与草案头）';

CREATE TABLE `course_curriculum_draft_series` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `seq` int NOT NULL COMMENT '列表顺序',
  `temp_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '草案临时 ID（原 tempId）',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '系列名',
  `sort_order` int DEFAULT NULL COMMENT '原 sortOrder',
  `positioning` text COLLATE utf8mb4_unicode_ci COMMENT '系列定位',
  PRIMARY KEY (`id`),
  KEY `idx_ccds_theme` (`theme_id`,`seq`),
  CONSTRAINT `fk_ccds_curriculum` FOREIGN KEY (`theme_id`) REFERENCES `course_curricula` (`theme_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表草案：系列';

CREATE TABLE `course_curriculum_draft_lessons` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `draft_series_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '草案系列 ID',
  `seq` int NOT NULL COMMENT '列表顺序',
  `lesson_no` int DEFAULT NULL COMMENT '课序号',
  `title` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题',
  `summary` text COLLATE utf8mb4_unicode_ci COMMENT '摘要',
  PRIMARY KEY (`id`),
  KEY `idx_ccdl_series` (`draft_series_id`,`seq`),
  CONSTRAINT `fk_ccdl_series` FOREIGN KEY (`draft_series_id`) REFERENCES `course_curriculum_draft_series` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表草案：课';

CREATE TABLE `course_curriculum_draft_outcomes` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `draft_lesson_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '草案课 ID',
  `seq` int NOT NULL COMMENT '顺序',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '学习成果',
  PRIMARY KEY (`id`),
  KEY `idx_ccdo_lesson` (`draft_lesson_id`,`seq`),
  CONSTRAINT `fk_ccdo_lesson` FOREIGN KEY (`draft_lesson_id`) REFERENCES `course_curriculum_draft_lessons` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表草案：课的学习成果';

CREATE TABLE `course_series` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系列名',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `ix_course_series_theme` (`theme_id`),
  CONSTRAINT `fk_course_series_theme` FOREIGN KEY (`theme_id`) REFERENCES `themes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程系列';

CREATE TABLE `course_lessons` (
  `work_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作品 ID（原 courseId）',
  `series_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属系列',
  `lesson_no` int DEFAULT NULL COMMENT '系列内课序号',
  `summary` text COLLATE utf8mb4_unicode_ci COMMENT '课程摘要',
  `status` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'draft' COMMENT '课程状态（原值原样保留）',
  `progress` int NOT NULL DEFAULT '0' COMMENT '处理进度 0–100',
  `series_positioning` text COLLATE utf8mb4_unicode_ci COMMENT '原 metadata.seriesPositioning',
  PRIMARY KEY (`work_id`),
  KEY `idx_course_lessons_series` (`series_id`,`lesson_no`),
  CONSTRAINT `fk_course_lessons_series` FOREIGN KEY (`series_id`) REFERENCES `course_series` (`id`),
  CONSTRAINT `fk_course_lessons_work` FOREIGN KEY (`work_id`) REFERENCES `works` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程模式作品扩展：课';

CREATE TABLE `course_lesson_outcomes` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `work_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作品 ID',
  `seq` int NOT NULL COMMENT '顺序',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '学习成果（原 metadata.learningOutcomes）',
  PRIMARY KEY (`id`),
  KEY `idx_course_lesson_outcomes` (`work_id`,`seq`),
  CONSTRAINT `fk_clo_lesson` FOREIGN KEY (`work_id`) REFERENCES `course_lessons` (`work_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课的学习成果';

CREATE TABLE `course_output_sources` (
  `output_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产出 ID',
  `sync_relative_path` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学习内容同步相对路径',
  `source_file_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '源文件',
  PRIMARY KEY (`output_id`),
  CONSTRAINT `fk_cos_output` FOREIGN KEY (`output_id`) REFERENCES `work_outputs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程产出的学习内容同步来源';

-- ---------------------------------------------------------------------------
-- 4.6 热点模式扩展
-- ---------------------------------------------------------------------------

CREATE TABLE `hotnews_radar_batches` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `job_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发任务',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'running' COMMENT '状态：running / completed / failed',
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '结束时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_hrb_theme` (`theme_id`,`created_at`),
  CONSTRAINT `fk_hrb_theme` FOREIGN KEY (`theme_id`) REFERENCES `themes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热点雷达采集批次';

CREATE TABLE `hotnews_topics` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `radar_batch_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '雷达批次',
  `radar_rank` int DEFAULT NULL COMMENT '批次内名次',
  `title` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `summary` text COLLATE utf8mb4_unicode_ci COMMENT '摘要',
  `translated_title` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '中文标题',
  `translated_summary` text COLLATE utf8mb4_unicode_ci COMMENT '中文事实速览',
  `translation_source_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '翻译源哈希',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态（HotNewsTopicStatuses 原值）',
  `heat_score` int NOT NULL DEFAULT '0' COMMENT '热度分',
  `writing_score` int NOT NULL DEFAULT '0' COMMENT '可写性分',
  `confidence_score` int NOT NULL DEFAULT '0' COMMENT '置信度分',
  `velocity_score` int NOT NULL DEFAULT '0' COMMENT '速度分',
  `freshness_score` int NOT NULL DEFAULT '0' COMMENT '新鲜度分',
  `evidence_score` int NOT NULL DEFAULT '0' COMMENT '证据分',
  `lifecycle` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'early' COMMENT '生命周期',
  `score_reason` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评分说明',
  `first_seen_at` datetime(3) DEFAULT NULL COMMENT '首次出现',
  `last_seen_at` datetime(3) DEFAULT NULL COMMENT '最近出现',
  `confirmed_at` datetime(3) DEFAULT NULL COMMENT '确认时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_hotnews_topics_theme_status` (`theme_id`,`status`,`updated_at`),
  KEY `idx_hotnews_topics_batch` (`radar_batch_id`,`radar_rank`),
  CONSTRAINT `fk_hotnews_topics_batch` FOREIGN KEY (`radar_batch_id`) REFERENCES `hotnews_radar_batches` (`id`),
  CONSTRAINT `fk_hotnews_topics_theme` FOREIGN KEY (`theme_id`) REFERENCES `themes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热点选题';

CREATE TABLE `hotnews_topic_score_metrics` (
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选题 ID（无此行即原空明细）',
  `source_count` int NOT NULL DEFAULT '0' COMMENT 'sourceCount',
  `official_count` int NOT NULL DEFAULT '0' COMMENT 'officialCount',
  `primary_count` int NOT NULL DEFAULT '0' COMMENT 'primaryCount',
  `secondary_count` int NOT NULL DEFAULT '0' COMMENT 'secondaryCount',
  `community_count` int NOT NULL DEFAULT '0' COMMENT 'communityCount',
  `unknown_date_count` int NOT NULL DEFAULT '0' COMMENT 'unknownDateCount',
  `independent_source_count` int NOT NULL DEFAULT '0' COMMENT 'independentSourceCount',
  `recent_source_count` int NOT NULL DEFAULT '0' COMMENT 'recentSourceCount',
  `ai_hits` int NOT NULL DEFAULT '0' COMMENT 'aiHits',
  `depth_hits` int NOT NULL DEFAULT '0' COMMENT 'depthHits',
  `source_growth_ratio` double NOT NULL DEFAULT '0' COMMENT 'sourceGrowthRatio',
  `freshness_hours` double NOT NULL DEFAULT '0' COMMENT 'freshnessHours',
  `metric_note` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'metricNote',
  `rubric_version` int DEFAULT NULL COMMENT 'rubricVersion',
  `recommend_use` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'recommendUse：lead / brief / watch / skip',
  `risk_note` text COLLATE utf8mb4_unicode_ci COMMENT 'riskNote',
  `origin_kind` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'originKind',
  PRIMARY KEY (`topic_id`),
  CONSTRAINT `fk_htsm_topic` FOREIGN KEY (`topic_id`) REFERENCES `hotnews_topics` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选题评分明细（标量部分）';

CREATE TABLE `hotnews_topic_score_dimensions` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选题 ID',
  `dimension_set` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '集合：current / rubric_snapshot',
  `seq` int NOT NULL COMMENT '顺序',
  `dimension_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'key',
  `label` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'label',
  `weight` int DEFAULT NULL COMMENT 'weight',
  `score` int DEFAULT NULL COMMENT 'score',
  `reason` text COLLATE utf8mb4_unicode_ci COMMENT 'reason',
  PRIMARY KEY (`id`),
  KEY `idx_htsd` (`topic_id`,`dimension_set`,`seq`),
  CONSTRAINT `fk_htsd_metrics` FOREIGN KEY (`topic_id`) REFERENCES `hotnews_topic_score_metrics` (`topic_id`) ON DELETE CASCADE,
  CONSTRAINT `ck_htsd_set` CHECK ((`dimension_set` in (_utf8mb4'current',_utf8mb4'rubric_snapshot')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选题评分维度';

CREATE TABLE `hotnews_topic_score_tags` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选题 ID',
  `tag_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：source_type / signal',
  `seq` int NOT NULL COMMENT '顺序',
  `tag_value` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '值',
  PRIMARY KEY (`id`),
  KEY `idx_htst` (`topic_id`,`tag_type`,`seq`),
  CONSTRAINT `fk_htst_metrics` FOREIGN KEY (`topic_id`) REFERENCES `hotnews_topic_score_metrics` (`topic_id`) ON DELETE CASCADE,
  CONSTRAINT `ck_htst_type` CHECK ((`tag_type` in (_utf8mb4'source_type',_utf8mb4'signal')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选题评分标签';

CREATE TABLE `hotnews_topic_sources` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选题 ID',
  `source_item_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '信源条目 ID',
  `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'evidence' COMMENT '角色',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_hts_topic` (`topic_id`),
  KEY `idx_hts_item` (`source_item_id`),
  CONSTRAINT `fk_hts_item` FOREIGN KEY (`source_item_id`) REFERENCES `source_items` (`id`),
  CONSTRAINT `fk_hts_topic` FOREIGN KEY (`topic_id`) REFERENCES `hotnews_topics` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选题与信源条目关联';

CREATE TABLE `hotnews_article_skeletons` (
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选题 ID',
  `raw_file_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI 原始返回（JSON 文件）',
  `working_title` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'workingTitle',
  `scope` text COLLATE utf8mb4_unicode_ci COMMENT 'scope',
  `core_thesis` text COLLATE utf8mb4_unicode_ci COMMENT 'coreThesis',
  `core_event` text COLLATE utf8mb4_unicode_ci COMMENT 'coreEvent',
  `narrative_shape` text COLLATE utf8mb4_unicode_ci COMMENT 'narrativeShape',
  `illustration_style` text COLLATE utf8mb4_unicode_ci COMMENT 'illustrationStyle',
  `cover_brief` text COLLATE utf8mb4_unicode_ci COMMENT 'coverBrief',
  `image_prompt_requirements` text COLLATE utf8mb4_unicode_ci COMMENT 'imagePromptRequirements',
  `recommended_word_count_option` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'recommendedWordCountOption',
  `recommended_image_count_option` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'recommendedImageCountOption',
  `updated_at_text` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'updatedAt（原字符串键）',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '行更新时间',
  PRIMARY KEY (`topic_id`),
  KEY `fk_has_raw` (`raw_file_id`),
  CONSTRAINT `fk_has_raw` FOREIGN KEY (`raw_file_id`) REFERENCES `stored_files` (`id`),
  CONSTRAINT `fk_has_topic` FOREIGN KEY (`topic_id`) REFERENCES `hotnews_topics` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热点文章骨架';

CREATE TABLE `hotnews_article_skeleton_items` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选题 ID',
  `item_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表类型：key_insight / impact / reader_signal / section_plan / architecture / flow / reference_hint',
  `seq` int NOT NULL COMMENT '顺序',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容',
  PRIMARY KEY (`id`),
  KEY `idx_hasi` (`topic_id`,`item_type`,`seq`),
  CONSTRAINT `fk_hasi_skeleton` FOREIGN KEY (`topic_id`) REFERENCES `hotnews_article_skeletons` (`topic_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热点文章骨架列表字段';

CREATE TABLE `hotnews_briefs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选题 ID',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'draft' COMMENT '状态：draft / confirmed',
  `title` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `overview` text COLLATE utf8mb4_unicode_ci COMMENT 'summary.overview',
  `core_thesis` text COLLATE utf8mb4_unicode_ci COMMENT 'summary.coreThesis',
  `core_event` text COLLATE utf8mb4_unicode_ci COMMENT 'summary.coreEvent',
  `narrative_shape` text COLLATE utf8mb4_unicode_ci COMMENT 'summary.narrativeShape',
  `confirmation_note` text COLLATE utf8mb4_unicode_ci COMMENT 'summary.confirmationNote',
  `confirmed_at` datetime(3) DEFAULT NULL COMMENT '确认时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_hotnews_briefs_topic` (`topic_id`,`created_at`),
  CONSTRAINT `fk_hotnews_briefs_topic` FOREIGN KEY (`topic_id`) REFERENCES `hotnews_topics` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热点选题简报';

CREATE TABLE `hotnews_brief_items` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `brief_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '简报 ID',
  `item_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表类型：confirmed_fact / reader_impact / recommended_angle / unknown',
  `seq` int NOT NULL COMMENT '顺序',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容',
  PRIMARY KEY (`id`),
  KEY `idx_hbi` (`brief_id`,`item_type`,`seq`),
  CONSTRAINT `fk_hbi_brief` FOREIGN KEY (`brief_id`) REFERENCES `hotnews_briefs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简报列表字段';

CREATE TABLE `hotnews_brief_sources` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `brief_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '简报 ID',
  `seq` int NOT NULL COMMENT '顺序',
  `row_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'HotNewsBriefSourceRow.id',
  `topic_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'topicId',
  `source_item_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'sourceItemId',
  `role` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'role',
  `title` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'title',
  `name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'name',
  `source_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'sourceName',
  `source_title` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'sourceTitle',
  `url` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'url',
  `source_url` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'sourceUrl',
  `summary` text COLLATE utf8mb4_unicode_ci COMMENT 'summary',
  PRIMARY KEY (`id`),
  KEY `idx_hbs` (`brief_id`,`seq`),
  CONSTRAINT `fk_hbs_brief` FOREIGN KEY (`brief_id`) REFERENCES `hotnews_briefs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简报来源快照';

CREATE TABLE `hotnews_works` (
  `work_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作品 ID（原热点 articleId）',
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选题 ID',
  `brief_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源简报',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'draft' COMMENT '状态：draft / generating / ready / rewritten',
  PRIMARY KEY (`work_id`),
  KEY `idx_hotnews_works_topic` (`topic_id`),
  KEY `idx_hotnews_works_brief` (`brief_id`),
  CONSTRAINT `fk_hotnews_works_brief` FOREIGN KEY (`brief_id`) REFERENCES `hotnews_briefs` (`id`),
  CONSTRAINT `fk_hotnews_works_topic` FOREIGN KEY (`topic_id`) REFERENCES `hotnews_topics` (`id`),
  CONSTRAINT `fk_hotnews_works_work` FOREIGN KEY (`work_id`) REFERENCES `works` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热点模式作品扩展：热点文章';

CREATE TABLE `hotnews_work_sources` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `work_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作品 ID',
  `seq` int NOT NULL COMMENT '顺序',
  `row_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'HotNewsBriefSourceRow.id',
  `topic_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'topicId',
  `source_item_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'sourceItemId',
  `role` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'role',
  `title` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'title',
  `name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'name',
  `source_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'sourceName',
  `source_title` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'sourceTitle',
  `url` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'url',
  `source_url` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'sourceUrl',
  `summary` text COLLATE utf8mb4_unicode_ci COMMENT 'summary',
  PRIMARY KEY (`id`),
  KEY `idx_hws` (`work_id`,`seq`),
  CONSTRAINT `fk_hws_work` FOREIGN KEY (`work_id`) REFERENCES `hotnews_works` (`work_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热点文章来源快照（生成时从简报复制）';

CREATE TABLE `hotnews_research_runs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选题 ID',
  `job_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务 ID',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '状态',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_hrr_topic` (`topic_id`,`created_at`),
  CONSTRAINT `fk_hrr_topic` FOREIGN KEY (`topic_id`) REFERENCES `hotnews_topics` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选题调研运行';

CREATE TABLE `hotnews_research_items` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `run_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调研运行 ID',
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选题 ID',
  `title` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `url` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链接',
  `summary` text COLLATE utf8mb4_unicode_ci COMMENT '摘要',
  `score` int NOT NULL DEFAULT '0' COMMENT '分数',
  `kind` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类型：source / web / manual',
  `has_score_details` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否有评分明细',
  `rubric_version` int DEFAULT NULL COMMENT '评分明细 rubricVersion',
  `recommend_use` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评分明细 recommendUse',
  `risk_note` text COLLATE utf8mb4_unicode_ci COMMENT '评分明细 riskNote',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_hri_topic` (`topic_id`,`score`),
  KEY `idx_hri_run` (`run_id`),
  CONSTRAINT `fk_hri_run` FOREIGN KEY (`run_id`) REFERENCES `hotnews_research_runs` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_hri_topic` FOREIGN KEY (`topic_id`) REFERENCES `hotnews_topics` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选题调研资料池条目';

CREATE TABLE `hotnews_research_item_dimensions` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `item_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资料条目 ID',
  `seq` int NOT NULL COMMENT '顺序',
  `dimension_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'key',
  `label` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'label',
  `weight` int DEFAULT NULL COMMENT 'weight',
  `score` int DEFAULT NULL COMMENT 'score',
  `reason` text COLLATE utf8mb4_unicode_ci COMMENT 'reason',
  PRIMARY KEY (`id`),
  KEY `idx_hrid` (`item_id`,`seq`),
  CONSTRAINT `fk_hrid_item` FOREIGN KEY (`item_id`) REFERENCES `hotnews_research_items` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资料条目评分维度';

CREATE TABLE `hotnews_research_conflicts` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `run_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调研运行 ID',
  `topic_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选题 ID',
  `claim` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冲突主张',
  `detail` text COLLATE utf8mb4_unicode_ci COMMENT '详情',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_hrc_topic` (`topic_id`),
  KEY `fk_hrc_run` (`run_id`),
  CONSTRAINT `fk_hrc_run` FOREIGN KEY (`run_id`) REFERENCES `hotnews_research_runs` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_hrc_topic` FOREIGN KEY (`topic_id`) REFERENCES `hotnews_topics` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选题调研冲突';

-- ---------------------------------------------------------------------------
-- 4.7 素材采集
-- ---------------------------------------------------------------------------

CREATE TABLE `source_channels` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  `channel_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型（原 type 值）',
  `base_url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '抓取入口 URL',
  `trust_level` int NOT NULL DEFAULT '0' COMMENT '可信度',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `last_success_at` datetime(3) DEFAULT NULL COMMENT '最近成功时间',
  `has_config` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否有抓取配置（原 config_json 非空）',
  `primary_query` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'config.query',
  `max_results` int DEFAULT NULL COMMENT 'config.maxResults',
  `search_topic` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'config.topic',
  `search_depth` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'config.searchDepth',
  `api_key_ciphertext` text COLLATE utf8mb4_unicode_ci COMMENT 'config.apiKey 密文',
  `date_field` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'config.dateField',
  `sort_mode` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'config.sort',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_source_channels_theme` (`theme_id`,`enabled`),
  CONSTRAINT `fk_source_channels_theme` FOREIGN KEY (`theme_id`) REFERENCES `themes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信源频道';

CREATE TABLE `source_channel_values` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `channel_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '频道 ID',
  `value_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：query（config.queries）/ url（config.urls）',
  `seq` int NOT NULL COMMENT '顺序',
  `value` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '值',
  PRIMARY KEY (`id`),
  KEY `idx_scv` (`channel_id`,`value_type`,`seq`),
  CONSTRAINT `fk_scv_channel` FOREIGN KEY (`channel_id`) REFERENCES `source_channels` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信源频道配置中的列表值';

CREATE TABLE `source_items` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `channel_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '频道 ID',
  `external_id` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '源站条目 ID',
  `url` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '链接',
  `title` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `author` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作者',
  `published_at` datetime(3) DEFAULT NULL COMMENT '发布时间',
  `summary` text COLLATE utf8mb4_unicode_ci COMMENT '摘要',
  `media_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'article / video',
  `site_label` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源站展示名',
  `lifecycle_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ready / pending_parse / discarded',
  `discard_reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '弃用原因',
  `content_intent` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'knowledge / marketing',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容哈希',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_source_items_theme_created` (`theme_id`,`created_at`),
  KEY `idx_source_items_channel` (`channel_id`),
  KEY `idx_source_items_hash` (`theme_id`,`content_hash`),
  KEY `idx_source_items_lifecycle` (`theme_id`,`lifecycle_status`,`media_type`),
  CONSTRAINT `fk_source_items_channel` FOREIGN KEY (`channel_id`) REFERENCES `source_channels` (`id`),
  CONSTRAINT `fk_source_items_theme` FOREIGN KEY (`theme_id`) REFERENCES `themes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信源抓取条目';

CREATE TABLE `source_fetch_runs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '运行状态',
  `hours` int NOT NULL DEFAULT '72' COMMENT '回溯小时数',
  `channel_count` int NOT NULL DEFAULT '0' COMMENT '频道数',
  `item_count` int NOT NULL DEFAULT '0' COMMENT '条目数',
  `new_items` int NOT NULL DEFAULT '0' COMMENT '新增条目数',
  `deduped_items` int NOT NULL DEFAULT '0' COMMENT '去重条目数',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `job_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务 ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_source_fetch_runs_theme` (`theme_id`,`created_at`),
  CONSTRAINT `fk_source_fetch_runs_theme` FOREIGN KEY (`theme_id`) REFERENCES `themes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信源抓取运行';

CREATE TABLE `source_fetch_channel_runs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `fetch_run_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '抓取运行 ID',
  `channel_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '频道 ID',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态',
  `items_count` int NOT NULL DEFAULT '0' COMMENT '条目数',
  `new_items` int NOT NULL DEFAULT '0' COMMENT '新增条目数',
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误码',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `started_at` datetime(3) DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_sfcr_fetch_run` (`fetch_run_id`),
  KEY `idx_sfcr_channel` (`channel_id`),
  CONSTRAINT `fk_sfcr_fetch_run` FOREIGN KEY (`fetch_run_id`) REFERENCES `source_fetch_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信源抓取分频道运行';

CREATE TABLE `source_query_site_blocks` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题 ID',
  `channel_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道类型（如 searxng）',
  `search_query` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '搜索词',
  `site_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '站点键（注册域）',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT 'active / revoked',
  `hit_count` int NOT NULL DEFAULT '0' COMMENT '累计命中偏题次数',
  `reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '拉黑原因摘要',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_query_site_block` (`theme_id`,`channel_type`,`search_query`,`site_key`),
  KEY `idx_query_site_block_theme_status` (`theme_id`,`status`),
  CONSTRAINT `fk_query_site_blocks_theme` FOREIGN KEY (`theme_id`) REFERENCES `themes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索词×网站主题级拉黑';

CREATE TABLE `source_query_site_block_samples` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `block_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '拉黑记录 ID',
  `seq` int NOT NULL COMMENT '顺序',
  `title` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '样例标题（最多 5 条）',
  PRIMARY KEY (`id`),
  KEY `idx_sqsbs` (`block_id`,`seq`),
  CONSTRAINT `fk_sqsbs_block` FOREIGN KEY (`block_id`) REFERENCES `source_query_site_blocks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索词站点拉黑的样例标题';

CREATE TABLE `video_transcripts` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `source_item_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '信源条目（原 owner_type=source_item）',
  `research_item_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资料条目（原 owner_type=topic_research_item）',
  `url` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '视频 URL 快照',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '解析状态：none / running / succeeded / failed',
  `step` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前或失败步骤',
  `error_text` text COLLATE utf8mb4_unicode_ci COMMENT '失败原因',
  `text_file_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '字幕或 ASR 原文文件',
  `content_kind` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容形态：speech / visual',
  `duration_seconds` int DEFAULT NULL COMMENT '时长秒',
  `parsed_at` datetime(3) DEFAULT NULL COMMENT '解析成功时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_video_transcripts_source_item` (`source_item_id`),
  UNIQUE KEY `uk_video_transcripts_research_item` (`research_item_id`),
  KEY `idx_video_transcripts_status` (`status`),
  KEY `fk_vt_text_file` (`text_file_id`),
  CONSTRAINT `fk_vt_research_item` FOREIGN KEY (`research_item_id`) REFERENCES `hotnews_research_items` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_vt_source_item` FOREIGN KEY (`source_item_id`) REFERENCES `source_items` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_vt_text_file` FOREIGN KEY (`text_file_id`) REFERENCES `stored_files` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频解析结果（每个条目一条，不跨条目复用）';

-- ---------------------------------------------------------------------------
-- 4.8 质量
-- ---------------------------------------------------------------------------

CREATE TABLE `audit_runs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键（可与任务 ID 相同，原规则不变）',
  `output_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产出 ID（原 document_id）',
  `job_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务 ID',
  `mode` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核模式：deterministic / full / repair',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '运行状态',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '被审正文哈希',
  `standard_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核标准版本：course-audit-v1 / hot-news-review-v1',
  `blocking_failed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否存在阻塞失败',
  `score` int DEFAULT NULL COMMENT '课程：分数',
  `passed` tinyint(1) DEFAULT NULL COMMENT '课程：是否通过',
  `warning_count` int DEFAULT NULL COMMENT '课程：警告数',
  `failed_count` int DEFAULT NULL COMMENT '课程：失败数',
  `result_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '热点：结果状态（原 result.status）',
  `decision_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人工决定状态',
  `decision_comment` text COLLATE utf8mb4_unicode_ci COMMENT '人工决定说明',
  `decided_at` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人工决定时间（原字符串）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_runs_output` (`output_id`,`created_at`),
  CONSTRAINT `fk_audit_runs_output` FOREIGN KEY (`output_id`) REFERENCES `work_outputs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核运行（课程与热点共用）';

CREATE TABLE `audit_issues` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `audit_run_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审核运行 ID',
  `seq` int NOT NULL COMMENT '顺序',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则码',
  `title` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：passed / warn / failed',
  `blocking` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否阻塞',
  `detail` text COLLATE utf8mb4_unicode_ci COMMENT '详情',
  `engine` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引擎：rule 等',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类',
  `severity` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '严重度',
  `repairable` tinyint(1) DEFAULT NULL COMMENT '是否可修复',
  `requires_human_confirmation` tinyint(1) DEFAULT NULL COMMENT '是否需人工确认',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_issues_run` (`audit_run_id`,`seq`),
  CONSTRAINT `fk_audit_issues_run` FOREIGN KEY (`audit_run_id`) REFERENCES `audit_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核问题明细（合并原 article_audit_issues 与 result_json.results）';

CREATE TABLE `audit_issue_refs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `issue_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题 ID',
  `ref_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：location / evidence',
  `seq` int NOT NULL COMMENT '顺序',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容（locations / evidence 元素 JSON）',
  PRIMARY KEY (`id`),
  KEY `idx_air` (`issue_id`,`ref_type`,`seq`),
  CONSTRAINT `fk_air_issue` FOREIGN KEY (`issue_id`) REFERENCES `audit_issues` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核问题的定位与证据';

CREATE TABLE `human_reviews` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `output_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产出 ID',
  `output_version_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '被审版本',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态',
  `comment` text COLLATE utf8mb4_unicode_ci COMMENT '评语',
  `reviewed_at` datetime(3) DEFAULT NULL COMMENT '审核时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_human_reviews_output` (`output_id`,`created_at`),
  CONSTRAINT `fk_human_reviews_output` FOREIGN KEY (`output_id`) REFERENCES `work_outputs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人工审核记录';

CREATE TABLE `repair_runs` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `output_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产出 ID',
  `job_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务 ID',
  `before_score` int DEFAULT NULL COMMENT '修复前分数',
  `after_score` int DEFAULT NULL COMMENT '修复后分数',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '状态',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_repair_runs_output` (`output_id`,`created_at`),
  CONSTRAINT `fk_repair_runs_output` FOREIGN KEY (`output_id`) REFERENCES `work_outputs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 修复运行';

CREATE TABLE `repair_run_codes` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `repair_run_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '修复运行 ID',
  `code_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：resolved / added',
  `seq` int NOT NULL COMMENT '顺序',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则码',
  PRIMARY KEY (`id`),
  KEY `idx_rrc` (`repair_run_id`,`code_type`,`seq`),
  CONSTRAINT `fk_rrc_run` FOREIGN KEY (`repair_run_id`) REFERENCES `repair_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='修复运行消除或新增的问题码';

CREATE TABLE `repair_candidates` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `audit_run_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源审核运行（原 review_run_id）',
  `output_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产出 ID',
  `job_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务 ID',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '状态：pending / accepted / rejected',
  `repair_all` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否全部修复（原 issue_ids 为空时）',
  `body_file_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '候选正文文件',
  `diff_summary` text COLLATE utf8mb4_unicode_ci COMMENT '差异摘要',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `decided_at` datetime(3) DEFAULT NULL COMMENT '决策时间',
  PRIMARY KEY (`id`),
  KEY `idx_repair_candidates_output` (`output_id`,`created_at`),
  KEY `fk_rc_file` (`body_file_id`),
  CONSTRAINT `fk_rc_file` FOREIGN KEY (`body_file_id`) REFERENCES `stored_files` (`id`),
  CONSTRAINT `fk_rc_output` FOREIGN KEY (`output_id`) REFERENCES `work_outputs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审稿修复候选（不占用正文版本号，接受后才生成新版本）';

CREATE TABLE `repair_candidate_issues` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `candidate_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '候选 ID',
  `seq` int NOT NULL COMMENT '顺序',
  `issue_ref` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题 ID（原 issueIds 元素）',
  PRIMARY KEY (`id`),
  KEY `idx_rci` (`candidate_id`,`seq`),
  CONSTRAINT `fk_rci_candidate` FOREIGN KEY (`candidate_id`) REFERENCES `repair_candidates` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='修复候选针对的问题';

-- ---------------------------------------------------------------------------
-- 4.9 排版与发布
-- ---------------------------------------------------------------------------

CREATE TABLE `layout_themes` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `theme_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定键（原 key）',
  `name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  `theme_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型（原 type）',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态',
  `current_version_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_layout_themes_key` (`theme_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排版主题';

CREATE TABLE `layout_theme_versions` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `layout_theme_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '排版主题 ID（原 theme_id）',
  `version_no` int NOT NULL COMMENT '版本号',
  `schema_version` int NOT NULL DEFAULT '1' COMMENT '配置格式版本',
  `config_json` json NOT NULL COMMENT '排版配置（配置文档）',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容哈希',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ltv_theme_version` (`layout_theme_id`,`version_no`),
  CONSTRAINT `fk_ltv_theme` FOREIGN KEY (`layout_theme_id`) REFERENCES `layout_themes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排版主题版本';

CREATE TABLE `output_layouts` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `output_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产出 ID',
  `output_version_no` int NOT NULL DEFAULT '1' COMMENT '对应正文版本号（原 article_version_no）',
  `layout_theme_version_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '排版主题版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_output_layouts_output` (`output_id`),
  CONSTRAINT `fk_output_layouts_output` FOREIGN KEY (`output_id`) REFERENCES `work_outputs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产出的排版选择（原 options_json 恒为空，已删除，视图仍返回空对象）';

CREATE TABLE `render_snapshots` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `output_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产出 ID',
  `output_version_no` int NOT NULL COMMENT '正文版本号',
  `layout_theme_version_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '排版主题版本',
  `layout_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '布局哈希',
  `markdown_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Markdown 哈希',
  `html_file_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渲染 HTML 文件',
  `html_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'HTML 哈希',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_render_snapshots_output` (`output_id`,`created_at`),
  KEY `fk_rs_file` (`html_file_id`),
  CONSTRAINT `fk_rs_file` FOREIGN KEY (`html_file_id`) REFERENCES `stored_files` (`id`),
  CONSTRAINT `fk_rs_output` FOREIGN KEY (`output_id`) REFERENCES `work_outputs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发布渲染快照（原图片清单与诊断恒为空，已删除，视图仍返回空列表）';

CREATE TABLE `channel_accounts` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `channel_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道：wechat_mp',
  `account_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '账号备注名',
  `app_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'AppID',
  `app_secret_ciphertext` text COLLATE utf8mb4_unicode_ci COMMENT 'AppSecret 密文',
  `default_author` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认作者',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_channel_accounts_channel` (`channel_code`),
  CONSTRAINT `fk_channel_accounts_channel` FOREIGN KEY (`channel_code`) REFERENCES `channels` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道账号（原 wechat_mp_settings，当前仅公众号单例）';

CREATE TABLE `publications` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `output_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产出 ID',
  `account_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道账号',
  `external_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道侧 ID（公众号草稿 media_id）',
  `pushed_at` datetime(3) NOT NULL COMMENT '最近一次成功推送时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_publications_output_account` (`output_id`,`account_id`),
  KEY `idx_publications_account_pushed` (`account_id`,`pushed_at`),
  CONSTRAINT `fk_publications_account` FOREIGN KEY (`account_id`) REFERENCES `channel_accounts` (`id`),
  CONSTRAINT `fk_publications_output` FOREIGN KEY (`output_id`) REFERENCES `work_outputs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道发布记录（原 wechat_mp_article_drafts，一产出一账号一行，推送时更新）';

CREATE TABLE `publication_metric_snapshots` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `publication_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发布记录',
  `captured_at` datetime(3) NOT NULL COMMENT '采集时间',
  `views` int DEFAULT NULL COMMENT '阅读/播放',
  `likes` int DEFAULT NULL COMMENT '点赞',
  `comments` int DEFAULT NULL COMMENT '评论',
  `shares` int DEFAULT NULL COMMENT '转发',
  `favorites` int DEFAULT NULL COMMENT '收藏',
  `followers_gained` int DEFAULT NULL COMMENT '带来关注',
  PRIMARY KEY (`id`),
  KEY `idx_pms` (`publication_id`,`captured_at`),
  CONSTRAINT `fk_pms_publication` FOREIGN KEY (`publication_id`) REFERENCES `publications` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发布效果数据快照（预留，本次不接代码）';

SET FOREIGN_KEY_CHECKS = 1;
