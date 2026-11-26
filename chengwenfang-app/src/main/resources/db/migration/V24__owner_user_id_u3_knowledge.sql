-- U3：知识库根表用户级隔离；历史回填种子管理员
-- shortcut: knowledge_index_state 仍为全局蓝绿单例，每用户独立活跃版本待 U3.1

ALTER TABLE `knowledge_documents`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `knowledge_chat_sessions`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `knowledge_memories`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `knowledge_retrieval_traces`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `knowledge_graph_edge_overrides`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `knowledge_answer_feedback`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

UPDATE `knowledge_documents` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `knowledge_chat_sessions` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `knowledge_memories` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `knowledge_retrieval_traces` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `knowledge_graph_edge_overrides` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
UPDATE `knowledge_answer_feedback` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';

ALTER TABLE `knowledge_documents` DROP INDEX `uk_knowledge_documents_source`;
ALTER TABLE `knowledge_documents`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD UNIQUE KEY `uk_knowledge_documents_owner_source` (`owner_user_id`, `source_kind`, `source_ref_id`),
  ADD KEY `idx_knowledge_documents_owner` (`owner_user_id`);

ALTER TABLE `knowledge_chat_sessions`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_knowledge_chat_sessions_owner` (`owner_user_id`);

ALTER TABLE `knowledge_memories`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_knowledge_memories_owner` (`owner_user_id`);

ALTER TABLE `knowledge_retrieval_traces`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_knowledge_retrieval_traces_owner` (`owner_user_id`);

ALTER TABLE `knowledge_graph_edge_overrides` DROP INDEX `uk_knowledge_graph_edge_uid`;
ALTER TABLE `knowledge_graph_edge_overrides`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD UNIQUE KEY `uk_knowledge_graph_edge_owner_uid` (`owner_user_id`, `neo4j_edge_uid`),
  ADD KEY `idx_knowledge_graph_edge_owner` (`owner_user_id`);

ALTER TABLE `knowledge_answer_feedback`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_knowledge_answer_feedback_owner` (`owner_user_id`);
