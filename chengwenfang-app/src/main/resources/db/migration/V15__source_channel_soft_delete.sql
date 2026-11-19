-- 信源渠道假删：删除写 deleted_at，列表/采集不再展示；行保留供历史条目外键关联。
ALTER TABLE `source_channels`
  ADD COLUMN `deleted_at` datetime(3) DEFAULT NULL COMMENT '假删时间；非空则不参与列表与采集' AFTER `updated_at`,
  ADD KEY `idx_source_channels_theme_alive` (`theme_id`, `deleted_at`, `enabled`);
