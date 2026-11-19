-- 长期记忆：向量 JSON + 暂停开关（Mem0 思路自研，无第三方 Memory SaaS）
ALTER TABLE `knowledge_memories`
  ADD COLUMN `embedding_json` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '嵌入向量 JSON 数组' AFTER `content`,
  ADD COLUMN `paused` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否暂停召回' AFTER `provenance`;
