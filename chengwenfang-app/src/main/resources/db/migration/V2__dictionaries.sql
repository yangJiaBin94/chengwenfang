-- P7.1：字典与平台基线行（表结构在 V1）
-- 校对：docs/refactor-v2/02-新表结构设计.md §4.1；AI 行沿用原 V1 baseline + V17

INSERT INTO content_modes (code, name, description, status, sort_order) VALUES
  ('course_system', '课程体系教学模式', '围绕主题规划、生成和维护系列课程内容。', 'enabled', 1),
  ('hot_news', '热点资讯模式', '从多来源发现热点，沉淀为值得深入写作的选题。', 'enabled', 2);

INSERT INTO channels (code, name, sort_order) VALUES
  ('master', '原稿', 10),
  ('wechat_mp', '微信公众号', 20);

INSERT INTO output_formats (code, name) VALUES
  ('article', '图文文章');

INSERT INTO channel_formats (channel_code, format_code) VALUES
  ('master', 'article'),
  ('wechat_mp', 'article');

-- 默认 AI 上游（text / image / vision）；密钥由设置页或测试桩写入
INSERT INTO ai_provider_configs (id, kind, name, model, base_url, api_key_ciphertext, protocol, enabled) VALUES
  (UUID(), 'text', 'Default Text', 'gpt-4.1-mini', 'https://api.openai.com/v1', NULL, 'openai_chat', 1),
  (UUID(), 'image', 'Default Image', 'gpt-image-2', 'https://api.openai.com/v1', NULL, 'openai_images', 1),
  (UUID(), 'vision', 'Default Vision', 'gpt-4o', 'https://api.openai.com/v1', NULL, 'openai_chat', 1);
