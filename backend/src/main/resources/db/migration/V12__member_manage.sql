-- v26 会员管理（B 端页面 109 menu:member）
-- 注：权限 id 223 = 当前 MAX(id)+1（220/221/222 已被 V4 加盟审批 / V5 保证金 / V6 目录复核占用）
-- 1) 新增按钮权限：会员启停（敏感操作，仅超管）
INSERT INTO permissions (id, code, name, module, type, parent_id, path, is_sensitive) VALUES
(223, 'member:edit', '会员启停', 'user', 2, 109, NULL, 1);

-- 2) 授权：仅超级管理员（role 1）可操作；
--    平台财务(2) 与店铺管理员(4) 沿用 V2 已授予的 109 菜单权，只读查看会员。
INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, 223
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = 1 AND permission_id = 223);
