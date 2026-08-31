-- =====================================================================
-- V5：加盟保证金收退确认（v12）——权限码
-- 保证金收退涉及资金（确认收款/退还），需独立权限码且为敏感项（仅超管可授予），
-- 门店管理员（角色 4）持有 menu:store 但不可触碰资金确认。
-- =====================================================================
INSERT INTO permissions (id, code, name, module, type, parent_id, path, is_sensitive) VALUES
(221, 'store:deposit:confirm', '加盟保证金收退', 'store', 2, 106, NULL, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 超级管理员（role 1）绑定新权限码（敏感权限仅总部）
INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, id FROM permissions WHERE code = 'store:deposit:confirm'
ON DUPLICATE KEY UPDATE permission_id = permission_id;
