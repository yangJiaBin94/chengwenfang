-- 设置写权限仅管理员：从 EDITOR 角色移除 settings:write
DELETE FROM `role_permissions`
WHERE `role_code` = 'EDITOR' AND `permission_code` = 'settings:write';
