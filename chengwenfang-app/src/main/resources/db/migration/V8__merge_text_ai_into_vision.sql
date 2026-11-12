-- 将历史 text 供应商配置合并进 vision，并删除 text 行。
-- 规则：text 有密钥时整行覆盖 vision（历史上写作主力是 text）；
--       text 无密钥时仅回填 vision 空白的 model / base_url。

UPDATE ai_provider_configs AS vision
INNER JOIN ai_provider_configs AS text_cfg
  ON text_cfg.kind = 'text'
SET
  vision.name = CASE
    WHEN text_cfg.api_key_ciphertext IS NOT NULL AND text_cfg.api_key_ciphertext <> ''
    THEN text_cfg.name
    ELSE vision.name
  END,
  vision.model = CASE
    WHEN text_cfg.api_key_ciphertext IS NOT NULL AND text_cfg.api_key_ciphertext <> ''
    THEN text_cfg.model
    WHEN (vision.model IS NULL OR vision.model = '')
      AND text_cfg.model IS NOT NULL AND text_cfg.model <> ''
    THEN text_cfg.model
    ELSE vision.model
  END,
  vision.base_url = CASE
    WHEN text_cfg.api_key_ciphertext IS NOT NULL AND text_cfg.api_key_ciphertext <> ''
    THEN text_cfg.base_url
    WHEN (vision.base_url IS NULL OR vision.base_url = '')
      AND text_cfg.base_url IS NOT NULL AND text_cfg.base_url <> ''
    THEN text_cfg.base_url
    ELSE vision.base_url
  END,
  vision.protocol = CASE
    WHEN text_cfg.api_key_ciphertext IS NOT NULL AND text_cfg.api_key_ciphertext <> ''
    THEN text_cfg.protocol
    ELSE vision.protocol
  END,
  vision.api_key_ciphertext = CASE
    WHEN text_cfg.api_key_ciphertext IS NOT NULL AND text_cfg.api_key_ciphertext <> ''
    THEN text_cfg.api_key_ciphertext
    ELSE vision.api_key_ciphertext
  END,
  vision.timeout_seconds = CASE
    WHEN text_cfg.api_key_ciphertext IS NOT NULL AND text_cfg.api_key_ciphertext <> ''
    THEN text_cfg.timeout_seconds
    ELSE vision.timeout_seconds
  END,
  vision.max_retries = CASE
    WHEN text_cfg.api_key_ciphertext IS NOT NULL AND text_cfg.api_key_ciphertext <> ''
    THEN text_cfg.max_retries
    ELSE vision.max_retries
  END,
  vision.temperature = CASE
    WHEN text_cfg.api_key_ciphertext IS NOT NULL AND text_cfg.api_key_ciphertext <> ''
    THEN text_cfg.temperature
    ELSE vision.temperature
  END,
  vision.max_output_tokens = CASE
    WHEN text_cfg.api_key_ciphertext IS NOT NULL AND text_cfg.api_key_ciphertext <> ''
    THEN text_cfg.max_output_tokens
    ELSE vision.max_output_tokens
  END,
  vision.enabled = CASE
    WHEN text_cfg.api_key_ciphertext IS NOT NULL AND text_cfg.api_key_ciphertext <> ''
    THEN text_cfg.enabled
    ELSE vision.enabled
  END,
  vision.updated_at = CURRENT_TIMESTAMP(3)
WHERE vision.kind = 'vision';

DELETE FROM ai_provider_configs WHERE kind = 'text';

ALTER TABLE ai_provider_configs
  MODIFY COLUMN `kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类别：image / vision / embedding';
