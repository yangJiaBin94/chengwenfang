-- 用户最近登录时间
ALTER TABLE users
  ADD COLUMN last_login_at DATETIME(3) NULL COMMENT '最近登录时间' AFTER updated_at;
