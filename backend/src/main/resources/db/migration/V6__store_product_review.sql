-- =====================================================================
-- V6：D14 目录变更复核（v13）——复核确认/驳回动作 + 复核状态存储
--
-- 背景：V1 已建 store_products.catalog_dirty（1=目录已更新待复核），
-- 但只有查询（listPendingCatalogReview），没有确认/驳回动作，也没有
-- 复核结果存储。本轮补闭环：
--   1. 权限码 store:product:review（按钮级，parent=102 商品管理菜单；
--      绑定超管 role 1 全量 + 门店管理员 role 4——复核是本店管理动作，
--      普通员工 role 5 不持有，列表可看、动作不可做）
--   2. store_products 加复核状态列：review_status（0待复核/1已确认/2已驳回）、
--      review_note（驳回原因）、reviewed_at / reviewed_by（复核审计）
-- =====================================================================
INSERT INTO permissions (id, code, name, module, type, parent_id, path, is_sensitive) VALUES
(222, 'store:product:review', '目录变更复核', 'store', 2, 102, NULL, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 超级管理员（role 1）全量绑定
INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, id FROM permissions WHERE code = 'store:product:review'
ON DUPLICATE KEY UPDATE permission_id = permission_id;

-- 门店管理员（role 4）绑定（V2 的 VALUES 快照不含新权限，需显式绑定）
INSERT INTO role_permissions (role_id, permission_id)
SELECT 4, id FROM permissions WHERE code = 'store:product:review'
ON DUPLICATE KEY UPDATE permission_id = permission_id;

-- 复核状态列（catalog_dirty 保留：1=目录已更新；review_status 记录复核结论）
ALTER TABLE store_products
  ADD COLUMN review_status TINYINT      NOT NULL DEFAULT 0 COMMENT '0待复核/1已确认/2已驳回（D14 店铺复核）' AFTER catalog_dirty,
  ADD COLUMN review_note  VARCHAR(255)  DEFAULT NULL     COMMENT '驳回原因（复核驳回时填写）' AFTER review_status,
  ADD COLUMN reviewed_at  DATETIME      DEFAULT NULL     COMMENT '复核时间' AFTER review_note,
  ADD COLUMN reviewed_by  BIGINT UNSIGNED DEFAULT NULL   COMMENT '复核人 admin_id' AFTER reviewed_at;
