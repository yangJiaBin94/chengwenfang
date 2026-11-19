-- 切块策略版本：算法升级后同正文仍需重切
ALTER TABLE `knowledge_documents`
  ADD COLUMN `chunk_strategy_version` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '切块策略版本' AFTER `content_hash`;
