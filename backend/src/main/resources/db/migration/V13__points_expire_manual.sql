-- v27 积分体系落地配套：手动触发积分过期回收（敏感运维操作，仅超管）
-- 注：权限 id 224 = 当前 MAX(id)+1（220/221/222/223 已被 V4/V5/V6/V12 占用）
INSERT INTO permissions (id, code, name, module, type, parent_id, path, is_sensitive) VALUES
(224, 'marketing:points:run', '积分过期回收执行', 'marketing', 2, 105, NULL, 1);

INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, 224
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = 1 AND permission_id = 224);
