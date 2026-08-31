-- =====================================================================
-- 养生茶小程序 初始数据（对齐设计文档 v10.0 第 5/6 章角色权限矩阵）
-- 预设角色 5 个 + 菜单/按钮/接口权限 + 初始超管 + 旗舰店 + 结算配置
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 预设角色（5.2）
-- ---------------------------------------------------------------------
INSERT INTO roles (id, code, name, data_scope, level, is_preset, description) VALUES
(1, 'SUPER_ADMIN',      '超级管理员', 'GLOBAL',       1, 1, 'Web 唯一可配分账/角色/全局审批的角色'),
(2, 'PLATFORM_FINANCE', '平台财务',   'GLOBAL',       1, 1, '流水、结算审核、对账'),
(3, 'WAREHOUSE',        '总部仓管',   'GLOBAL',       1, 1, '总部统一发货、退货验货（发货任务按店分组可见）'),
(4, 'STORE_ADMIN',      '店铺管理员', 'SINGLE_STORE', 2, 1, '本店全权：选品定价/订单/退款审批/催发货/代客下单/本店营销/员工账号'),
(5, 'STORE_STAFF',      '店铺员工',   'SINGLE_STORE', 2, 1, '本店基础操作：订单查询、退款申请提交');

-- ---------------------------------------------------------------------
-- 2. 权限（三级控制 5.5：菜单 → 按钮 → 接口；is_sensitive=1 超管专属）
-- ---------------------------------------------------------------------
INSERT INTO permissions (id, code, name, module, type, parent_id, path, is_sensitive) VALUES
-- 菜单权限（type=1）
(101, 'menu:dashboard',  '工作台',       'auth',       1, NULL, '/dashboard', 0),
(102, 'menu:product',    '商品管理',     'product',    1, NULL, '/product',   0),
(103, 'menu:order',      '订单管理',     'order',      1, NULL, '/order',     0),
(104, 'menu:refund',     '退款/售后',    'order',      1, NULL, '/refund',    0),
(105, 'menu:marketing',  '营销管理',     'marketing',  1, NULL, '/marketing', 0),
(106, 'menu:store',      '门店管理',     'store',      1, NULL, '/store',     0),
(107, 'menu:settlement', '结算管理',     'settlement', 1, NULL, '/settlement',0),
(108, 'menu:inventory',  '库存管理',     'product',    1, NULL, '/inventory', 0),
(109, 'menu:member',     '会员管理',     'user',       1, NULL, '/member',    0),
(110, 'menu:system',     '系统设置',     'auth',       1, NULL, '/system',    0),
-- 按钮权限（type=2）
(201, 'product:edit',          '商品编辑/定价',   'product',    2, 102, NULL, 0),
(202, 'product:cost:view',     '成本价查看',       'product',    2, 102, NULL, 1),
(203, 'product:catalog:sync',  '目录强制同步',     'product',    2, 102, NULL, 1),
(204, 'order:ship',            '订单发货',         'order',      2, 103, NULL, 0),
(205, 'order:urge:handle',     '催发货处理',       'order',      2, 103, NULL, 0),
(206, 'order:delete',          '删除订单',         'order',      2, 103, NULL, 1),
(207, 'order:create:behalf',   '代客下单',         'order',      2, 103, NULL, 0),
(208, 'refund:approve',        '退款审批',         'order',      2, 104, NULL, 0),
(209, 'refund:submit',         '退款申请提交',     'order',      2, 104, NULL, 0),
(210, 'return:inspect',        '退货验货',         'order',      2, 104, NULL, 0),
(211, 'marketing:coupon',      '优惠券管理',       'marketing',  2, 105, NULL, 0),
(212, 'marketing:promotion',   '促销活动管理',     'marketing',  2, 105, NULL, 0),
(213, 'settlement:review',     '结算单审核',       'settlement', 2, 107, NULL, 0),
(214, 'settlement:payout',     '打款确认',         'settlement', 2, 107, NULL, 1),
(215, 'settlement:reconcile',  '对账',             'settlement', 2, 107, NULL, 0),
(216, 'inventory:manage',      '库存入库/盘点',    'product',    2, 108, NULL, 0),
(217, 'store:staff:manage',    '员工账号管理',     'store',      2, 106, NULL, 0),
(218, 'system:role:config',    '角色配置',         'auth',       2, 110, NULL, 1),
(219, 'system:settle:config',  '分账比例配置',     'auth',       2, 110, NULL, 1);

-- ---------------------------------------------------------------------
-- 3. 角色-权限分配（对齐第 6 章角色权限矩阵）
-- ---------------------------------------------------------------------
-- 超级管理员：全部权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, id FROM permissions;

-- 平台财务：结算/退款查询/对账（不含打款确认与角色配置等敏感项）
INSERT INTO role_permissions (role_id, permission_id) VALUES
(2, 101), (2, 104), (2, 107), (2, 109), (2, 213), (2, 215);

-- 总部仓管：发货/退货验货/库存
INSERT INTO role_permissions (role_id, permission_id) VALUES
(3, 101), (3, 103), (3, 104), (3, 108), (3, 204), (3, 205), (3, 210), (3, 216);

-- 店铺管理员：本店全权（敏感权限除外）
INSERT INTO role_permissions (role_id, permission_id) VALUES
(4, 101), (4, 102), (4, 103), (4, 104), (4, 105), (4, 106), (4, 109),
(4, 201), (4, 207), (4, 208), (4, 211), (4, 212), (4, 217);

-- 店铺员工：订单查询 + 退款申请提交
INSERT INTO role_permissions (role_id, permission_id) VALUES
(5, 101), (5, 103), (5, 104), (5, 209);

-- ---------------------------------------------------------------------
-- 4. 初始超管（首次登录必须修改密码）
--    password_hash 为占位值，应用启动时检测并强制走 CLI 重置：
--    java -jar app.jar --reset-admin-password
-- ---------------------------------------------------------------------
INSERT INTO admin_users (id, username, password_hash, real_name, phone, role_id, status, token_version)
VALUES (1, 'admin', '$2a$10$PLACEHOLDER.RESET.VIA.CLI.BEFORE.FIRST.LOGIN', '超级管理员', NULL, 1, 1, 0);

-- ---------------------------------------------------------------------
-- 5. 直营旗舰店（ST001）+ 结算配置默认值
-- ---------------------------------------------------------------------
INSERT INTO stores (id, store_no, store_name, store_type, status, contact_name)
VALUES (1, 'ST001', '品牌直营旗舰店', 1, 1, '总部');

INSERT INTO store_settlement_configs (store_id, commission_rate, cycle_type, auto_confirm_hours)
VALUES (1, 0.0500, 1, 72);

-- ---------------------------------------------------------------------
-- 6. 初始商品分类（可按实际业务调整）
-- ---------------------------------------------------------------------
INSERT INTO product_categories (id, name, sort, status) VALUES
(1, '养生茶饮',  1, 1),
(2, '滋补干货',  2, 1),
(3, '礼盒套装',  3, 1),
(4, '茶具配件',  4, 1);
