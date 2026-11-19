-- 跨主题信源沉淀池：带场景标签，供热点主题启用时隐式匹配克隆。
CREATE TABLE `source_channel_catalog` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务键，如 sopilot_ai',
  `name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名',
  `channel_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '抓取类型：rss/web/…',
  `base_url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '抓取入口 URL',
  `config_json` text COLLATE utf8mb4_unicode_ci COMMENT '可选抓取配置 JSON',
  `tags_json` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '[]' COMMENT '场景标签 JSON 数组',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '匹配排序，越小越优先',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_channel_catalog_code` (`code`),
  KEY `idx_source_channel_catalog_enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信源沉淀池目录';

INSERT INTO `source_channel_catalog`
  (`id`, `code`, `name`, `channel_type`, `base_url`, `config_json`, `tags_json`, `enabled`, `sort_order`)
VALUES
  ('a1000000-0000-4000-8000-000000000001', 'sopilot_ai', 'SoPilot AI 起爆帖', 'rss',
   'https://sopilot.net/rss/hottweets', NULL, '["ai"]', 1, 10),
  ('a1000000-0000-4000-8000-000000000002', 'sopilot_web3', 'SoPilot Web3 起爆帖', 'rss',
   'https://sopilot.net/rss/hottweets?type=Web3', NULL, '["web3"]', 1, 20);
