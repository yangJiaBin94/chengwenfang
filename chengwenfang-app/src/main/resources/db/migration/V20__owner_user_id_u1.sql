-- U1：高敏根表增加用户级隔离列；历史数据回填到种子管理员

ALTER TABLE `ai_provider_configs`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

ALTER TABLE `jobs`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

ALTER TABLE `stored_files`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

ALTER TABLE `channel_accounts`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

UPDATE `ai_provider_configs` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL;
UPDATE `jobs` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL;
UPDATE `stored_files` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL;
UPDATE `channel_accounts` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL;

ALTER TABLE `ai_provider_configs` DROP INDEX `kind`;
ALTER TABLE `ai_provider_configs`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD UNIQUE KEY `uk_ai_provider_owner_kind` (`owner_user_id`, `kind`),
  ADD KEY `idx_ai_provider_owner` (`owner_user_id`);

ALTER TABLE `jobs`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_jobs_owner` (`owner_user_id`);

ALTER TABLE `stored_files`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_stored_files_owner` (`owner_user_id`);

ALTER TABLE `channel_accounts`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_channel_accounts_owner` (`owner_user_id`);
