-- 用户头像：关联 stored_files.id，可空
ALTER TABLE users
  ADD COLUMN avatar_file_id CHAR(36) NULL COMMENT '头像文件 ID' AFTER display_name;
