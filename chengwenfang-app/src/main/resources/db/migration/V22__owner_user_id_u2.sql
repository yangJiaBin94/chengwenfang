-- U2：内容根表用户级隔离
ALTER TABLE `themes`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `works`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;
ALTER TABLE `media_assets`
  ADD COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NULL COMMENT '所属用户' AFTER `id`;

UPDATE `themes` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL;
UPDATE `works` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL;
UPDATE `media_assets` SET `owner_user_id` = '00000000-0000-4000-8000-000000000001' WHERE `owner_user_id` IS NULL;

ALTER TABLE `themes`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_themes_owner` (`owner_user_id`);
ALTER TABLE `works`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_works_owner` (`owner_user_id`);
ALTER TABLE `media_assets`
  MODIFY COLUMN `owner_user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户',
  ADD KEY `idx_media_assets_owner` (`owner_user_id`);
