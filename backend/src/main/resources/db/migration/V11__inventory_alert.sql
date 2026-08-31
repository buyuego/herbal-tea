-- v25 库存管理（B 端页面 108 menu:inventory）
-- 1) SKU 低库存预警阈值（总部总仓维度，默认 10）
ALTER TABLE product_skus
    ADD COLUMN alert_stock INT NOT NULL DEFAULT 10 COMMENT '低库存预警阈值' AFTER stock;

-- 2) 库存流水补索引：按时间倒序分页（列表默认排序 created_at DESC）
ALTER TABLE inventory_records
    ADD KEY idx_ir_created (created_at);
