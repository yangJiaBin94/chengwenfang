-- U3.1：知识库索引蓝绿状态按用户拆分（原 id=1 全站单例）

CREATE TABLE `knowledge_index_state_per_owner` (
  `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户 ID',
  `active_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'v1' COMMENT '当前读路径索引版本',
  `writing_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '全量重建进行中的写入版本',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库索引活跃版本（每用户一行）';

-- 种子管理员继承原单例 active/writing（无单例行则默认 v1）
INSERT INTO `knowledge_index_state_per_owner` (`owner_user_id`, `active_version`, `writing_version`, `updated_at`)
SELECT
  '00000000-0000-4000-8000-000000000001',
  COALESCE(
    (SELECT COALESCE(NULLIF(TRIM(s.`active_version`), ''), 'v1') FROM `knowledge_index_state` s WHERE s.`id` = 1),
    'v1'
  ),
  (SELECT NULLIF(TRIM(s.`writing_version`), '') FROM `knowledge_index_state` s WHERE s.`id` = 1),
  CURRENT_TIMESTAMP(3)
ON DUPLICATE KEY UPDATE
  `active_version` = VALUES(`active_version`),
  `writing_version` = VALUES(`writing_version`),
  `updated_at` = VALUES(`updated_at`);

-- 已有文档归属用户补行（共用迁出的 active，保证现有切块可读）
INSERT IGNORE INTO `knowledge_index_state_per_owner` (`owner_user_id`, `active_version`, `writing_version`, `updated_at`)
SELECT DISTINCT
  d.`owner_user_id`,
  COALESCE(
    (SELECT COALESCE(NULLIF(TRIM(s.`active_version`), ''), 'v1') FROM `knowledge_index_state` s WHERE s.`id` = 1),
    'v1'
  ),
  NULL,
  CURRENT_TIMESTAMP(3)
FROM `knowledge_documents` d
WHERE d.`owner_user_id` IS NOT NULL
  AND TRIM(d.`owner_user_id`) != '';

-- 原子换表，避免先 DROP 后中断导致无表
RENAME TABLE
  `knowledge_index_state` TO `knowledge_index_state_legacy_singleton`,
  `knowledge_index_state_per_owner` TO `knowledge_index_state`;

DROP TABLE `knowledge_index_state_legacy_singleton`;
