-- 幂等：U1/U2 表空串或异常空值回填种子管理员（与 V19 用户 id 一致）
UPDATE `ai_provider_configs`
SET `owner_user_id` = '00000000-0000-4000-8000-000000000001'
WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';

UPDATE `jobs`
SET `owner_user_id` = '00000000-0000-4000-8000-000000000001'
WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';

UPDATE `stored_files`
SET `owner_user_id` = '00000000-0000-4000-8000-000000000001'
WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';

UPDATE `channel_accounts`
SET `owner_user_id` = '00000000-0000-4000-8000-000000000001'
WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';

UPDATE `themes`
SET `owner_user_id` = '00000000-0000-4000-8000-000000000001'
WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';

UPDATE `works`
SET `owner_user_id` = '00000000-0000-4000-8000-000000000001'
WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';

UPDATE `media_assets`
SET `owner_user_id` = '00000000-0000-4000-8000-000000000001'
WHERE `owner_user_id` IS NULL OR TRIM(`owner_user_id`) = '';
