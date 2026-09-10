-- v32 站内通知中心（B 端顶部铃铛 + /notification 通知页）
-- 1) 通知表（无 updated_at 列，实体继承 BaseCreatedOnlyEntity）
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知id',
    recipient_id BIGINT UNSIGNED NOT NULL COMMENT '接收者管理员id（users.id）',
    recipient_role TINYINT NOT NULL COMMENT '冗余：接收者角色id，便于按角色快速统计',
    store_id BIGINT UNSIGNED DEFAULT NULL COMMENT '业务门店id（用于数据隔离；NULL=超管/财务全平台事件）',
    biz_type VARCHAR(32) NOT NULL COMMENT '业务类型：order/refund/settlement/inventory/member',
    biz_id VARCHAR(64) NOT NULL COMMENT '业务id（订单号/退款单号/结算单号等）',
    title VARCHAR(128) NOT NULL COMMENT '通知标题',
    content VARCHAR(512) NOT NULL COMMENT '通知正文',
    link VARCHAR(255) DEFAULT NULL COMMENT '点击跳转路径（前端路由）',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0未读/1已读',
    read_at DATETIME DEFAULT NULL COMMENT '已读时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_n_recipient_unread (recipient_id, is_read, created_at),
    KEY idx_n_biz (biz_type, biz_id),
    KEY idx_n_created (created_at),
    KEY idx_n_store (store_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表（v32）';

-- 2) 新增菜单权限 111 menu:notification（铃铛+通知页）
--    注意：当前 MAX(permissions.id)=224（V13 占用），新按钮权限用 226
--    全角色默认可见（V2 各角色已有 101~110 菜单），这里只需加新菜单 + 默认授权全角色
INSERT INTO permissions (id, code, name, module, type, parent_id, path, is_sensitive) VALUES
(111, 'menu:notification', '通知中心', 'notification', 1, NULL, '/notification', 0),
(226, 'notification:manage', '通知管理（广播/清理）', 'notification', 2, 111, NULL, 1);

-- 3) 默认授权：所有角色可见 menu:notification（铃铛是工作台必备）
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, 111
FROM roles r
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = r.id AND permission_id = 111);

-- 4) 仅超管授权 notification:manage（敏感操作）
INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, 226
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = 1 AND permission_id = 226);