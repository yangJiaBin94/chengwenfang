-- 用户最近登录 IP 与市级属地
ALTER TABLE users
  ADD COLUMN last_login_ip VARCHAR(64) NULL COMMENT '最近登录 IP' AFTER last_login_at,
  ADD COLUMN last_login_region VARCHAR(64) NULL COMMENT '最近登录属地（市级）' AFTER last_login_ip;
