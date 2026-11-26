-- 单实例用户鉴权：用户 / 角色 / 权限（非 SaaS，无租户）
-- 种子管理员 id 固定，供后续 owner_user_id 回填引用

CREATE TABLE `users` (
  `id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录名（唯一）',
  `password_hash` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希（BCrypt）',
  `display_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '展示名',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录用户';

CREATE TABLE `roles` (
  `code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码：ADMIN / EDITOR / VIEWER',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色字典';

CREATE TABLE `permissions` (
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限字典';

CREATE TABLE `role_permissions` (
  `role_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  `permission_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
  PRIMARY KEY (`role_code`, `permission_code`),
  CONSTRAINT `fk_role_permissions_role` FOREIGN KEY (`role_code`) REFERENCES `roles` (`code`),
  CONSTRAINT `fk_role_permissions_perm` FOREIGN KEY (`permission_code`) REFERENCES `permissions` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限';

CREATE TABLE `user_roles` (
  `user_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 ID',
  `role_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  PRIMARY KEY (`user_id`, `role_code`),
  CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_code`) REFERENCES `roles` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色';

INSERT INTO `roles` (`code`, `name`) VALUES
  ('ADMIN', '管理员'),
  ('EDITOR', '编辑'),
  ('VIEWER', '只读');

INSERT INTO `permissions` (`code`, `name`) VALUES
  ('settings:write', '修改系统设置'),
  ('user:manage', '管理用户'),
  ('work:write', '读写作品内容'),
  ('work:read', '读取作品内容');

INSERT INTO `role_permissions` (`role_code`, `permission_code`) VALUES
  ('ADMIN', 'settings:write'),
  ('ADMIN', 'user:manage'),
  ('ADMIN', 'work:write'),
  ('ADMIN', 'work:read'),
  ('EDITOR', 'work:write'),
  ('EDITOR', 'work:read'),
  ('VIEWER', 'work:read');

-- 密码：admin123（BCrypt，Hutool BCrypt.hashpw）
INSERT INTO `users` (`id`, `username`, `password_hash`, `display_name`, `enabled`) VALUES
  ('00000000-0000-4000-8000-000000000001', 'admin',
   '$2a$10$6cS5.cxqlbmMG4gZekwlGOjfP.AWFUPex3F6Er.AePenBlUOnRzQW',
   '管理员', 1);

INSERT INTO `user_roles` (`user_id`, `role_code`) VALUES
  ('00000000-0000-4000-8000-000000000001', 'ADMIN');
