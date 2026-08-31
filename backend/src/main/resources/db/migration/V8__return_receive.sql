-- v20 退款售后：总部收货审计列（联测发现缺口补齐）
-- 验货（warehouse_status=2 已收货 → 3/4）前置需要「总部收货」动作，
-- 与 inspected_by 对称，补 received_by 记录收货仓管，支持审计留痕。

ALTER TABLE return_orders
  ADD COLUMN received_by BIGINT UNSIGNED DEFAULT NULL COMMENT '总部收货人（admin_users.id）' AFTER warehouse_status;
