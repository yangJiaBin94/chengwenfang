-- 知识库 RAG P0–P4：版本蓝绿、切块 provenance、会话记忆、长期记忆、检索轨迹、图边纠错旁路表

CREATE TABLE IF NOT EXISTS `knowledge_index_state` (
  `id` tinyint NOT NULL COMMENT '单例行，固定为 1',
  `active_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'v1' COMMENT '当前读路径索引版本',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库索引活跃版本';

INSERT INTO `knowledge_index_state` (`id`, `active_version`) VALUES (1, 'v1')
  ON DUPLICATE KEY UPDATE `id` = `id`;

ALTER TABLE `knowledge_chunks`
  ADD COLUMN `parent_chunk_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父块 ID（子块检索/父块进窗）' AFTER `document_id`,
  ADD COLUMN `char_span_start` int DEFAULT NULL COMMENT '原文起始偏移' AFTER `heading_path`,
  ADD COLUMN `char_span_end` int DEFAULT NULL COMMENT '原文结束偏移' AFTER `char_span_start`,
  ADD COLUMN `index_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'v1' COMMENT '索引版本' AFTER `embedding_model`,
  ADD COLUMN `indexed_at` datetime(3) DEFAULT NULL COMMENT '写入索引时间' AFTER `index_version`,
  ADD KEY `idx_knowledge_chunks_version` (`index_version`),
  ADD KEY `idx_knowledge_chunks_parent` (`parent_chunk_id`);

ALTER TABLE `knowledge_chat_sessions`
  ADD COLUMN `summary` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '会话滚动摘要' AFTER `title`,
  ADD COLUMN `memory_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用会话记忆' AFTER `summary`;

CREATE TABLE IF NOT EXISTS `knowledge_memories` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `scope_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default' COMMENT '记忆作用域',
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记忆正文',
  `provenance` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '出处说明',
  `expires_at` datetime(3) DEFAULT NULL COMMENT '过期时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_memories_scope` (`scope_key`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库长期记忆';

CREATE TABLE IF NOT EXISTS `knowledge_retrieval_traces` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `session_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话 ID',
  `round_no` int NOT NULL DEFAULT 0 COMMENT '轮次',
  `mode_key` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检索模式',
  `query_text` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '查询或改写后查询',
  `payload_json` json DEFAULT NULL COMMENT '命中/分数/是否 rerank/拒答原因等',
  `latency_ms` int NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_retrieval_traces_session` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库检索观测轨迹';

CREATE TABLE IF NOT EXISTS `knowledge_graph_edge_overrides` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `neo4j_edge_uid` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Neo4j 边业务 UID',
  `source_chunk_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源切块',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `confidence` double DEFAULT NULL COMMENT '置信度',
  `note` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '纠错说明',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_graph_edge_uid` (`neo4j_edge_uid`),
  KEY `idx_knowledge_graph_edge_chunk` (`source_chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图边纠错与启用旁路（权威开关在应用侧）';
