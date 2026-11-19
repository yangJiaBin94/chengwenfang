-- 沉淀池补强：已测通的 AI / Web3 官方或稳定第三方 RSS/Atom（不依赖聚合站 SPA）。
-- 日常维护以 scripts/bootstrap/source-channel-catalog.json 为准（bootstrap-seed 导入）；本迁移仅作空库基线。
-- 幂等：已存在同 code 则跳过，便于本地先手工灌数后再跑 Flyway。
INSERT IGNORE INTO `source_channel_catalog`
  (`id`, `code`, `name`, `channel_type`, `base_url`, `config_json`, `tags_json`, `enabled`, `sort_order`)
VALUES
  ('a1000000-0000-4000-8000-000000000003', 'hnrss_ai', 'Hacker News AI 过滤', 'rss',
   'https://hnrss.org/newest?q=AI+OR+LLM', NULL, '["ai"]', 1, 15),
  ('a1000000-0000-4000-8000-000000000004', 'mit_tr_ai', 'MIT Technology Review AI', 'rss',
   'https://www.technologyreview.com/topic/artificial-intelligence/feed', NULL, '["ai"]', 1, 25),
  ('a1000000-0000-4000-8000-000000000005', 'simonwillison', 'Simon Willison Weblog', 'rss',
   'https://simonwillison.net/atom/everything/', NULL, '["ai"]', 1, 30),
  ('a1000000-0000-4000-8000-000000000006', 'lilianweng', 'Lilian Weng Blog', 'rss',
   'https://lilianweng.github.io/index.xml', NULL, '["ai"]', 1, 35),
  ('a1000000-0000-4000-8000-000000000007', 'decrypt', 'Decrypt', 'rss',
   'https://decrypt.co/feed', NULL, '["web3"]', 1, 22),
  ('a1000000-0000-4000-8000-000000000008', 'cointelegraph', 'Cointelegraph', 'rss',
   'https://cointelegraph.com/rss', NULL, '["web3"]', 1, 24),
  ('a1000000-0000-4000-8000-000000000009', 'bitcoin_magazine', 'Bitcoin Magazine', 'rss',
   'https://bitcoinmagazine.com/.rss/full/', NULL, '["web3"]', 1, 26);
