-- 知识库 RAG：统一文档 / 切块 / 会话（向量在 Qdrant）
CREATE TABLE `knowledge_documents` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `source_kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源种类',
  `source_ref_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源侧主键',
  `media_kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '介质种类',
  `title` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '标题',
  `theme_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主题 ID',
  `origin_file_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原始附件文件',
  `text_file_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '归一化文本快照文件',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '可检索文本哈希',
  `source_version_stamp` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源版本戳',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '索引状态',
  `chunk_count` int NOT NULL DEFAULT '0' COMMENT '切块数量',
  `error_message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败原因',
  `deep_link` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端深链',
  `payload_json` json DEFAULT NULL COMMENT '扩展标签等调试快照',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_documents_source` (`source_kind`,`source_ref_id`),
  KEY `idx_knowledge_documents_status` (`status`,`updated_at`),
  KEY `idx_knowledge_documents_theme` (`theme_id`),
  KEY `fk_kd_origin_file` (`origin_file_id`),
  KEY `fk_kd_text_file` (`text_file_id`),
  CONSTRAINT `fk_kd_origin_file` FOREIGN KEY (`origin_file_id`) REFERENCES `stored_files` (`id`),
  CONSTRAINT `fk_kd_text_file` FOREIGN KEY (`text_file_id`) REFERENCES `stored_files` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档索引快照';

CREATE TABLE `knowledge_chunks` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `document_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档 ID',
  `chunk_index` int NOT NULL COMMENT '块序号',
  `heading_path` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题路径',
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '块正文',
  `token_estimate` int NOT NULL DEFAULT '0' COMMENT '字数估计',
  `qdrant_point_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Qdrant 点 ID',
  `embedding_model` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '嵌入模型名',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_chunks_doc_index` (`document_id`,`chunk_index`),
  KEY `idx_knowledge_chunks_point` (`qdrant_point_id`),
  FULLTEXT KEY `ft_knowledge_chunks_content` (`content`) WITH PARSER ngram,
  CONSTRAINT `fk_kc_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_documents` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库切块（向量在 Qdrant）';

CREATE TABLE `knowledge_chat_sessions` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `title` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '会话标题',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_chat_sessions_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库对话会话';

CREATE TABLE `knowledge_chat_messages` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `session_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话 ID',
  `role_key` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色：user / assistant',
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息正文',
  `payload_json` json DEFAULT NULL COMMENT '引用等扩展快照',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_chat_messages_session` (`session_id`,`created_at`),
  CONSTRAINT `fk_kcm_session` FOREIGN KEY (`session_id`) REFERENCES `knowledge_chat_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库对话消息';

INSERT INTO ai_provider_configs (id, kind, name, model, base_url, api_key_ciphertext, protocol, enabled) VALUES
  (UUID(), 'embedding', 'Default Embedding', 'text-embedding-3-small', 'https://api.openai.com/v1', NULL, 'openai_embeddings', 1);
