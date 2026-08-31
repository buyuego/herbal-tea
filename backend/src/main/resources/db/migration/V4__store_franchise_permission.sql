-- =====================================================================
-- V4：Store 模块骨架（v9）——加盟审批权限码
-- 总部专属操作（申请列表/审批/拒绝/管理员绑定）需要独立权限码，
-- 不能复用 menu:store（门店管理员角色 4 也持有，会误放行总部操作）。
-- V2 的超管全量绑定是 INSERT..SELECT 一次性快照，新权限需显式绑定。
-- =====================================================================
INSERT INTO permissions (id, code, name, module, type, parent_id, path, is_sensitive) VALUES
(220, 'store:franchise:approve', '加盟审批', 'store', 2, 106, NULL, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 超级管理员（role 1）绑定新权限码（敏感权限仅总部）
INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, id FROM permissions WHERE code = 'store:franchise:approve'
ON DUPLICATE KEY UPDATE permission_id = permission_id;
