-- v30 促销活动全链路：订单记录命中的活动与优惠金额，结算按 scope 分词活动成本
-- 计价顺序（v30 约定）：活动优惠 → 优惠券 → 积分抵扣，后续环节门槛均按前序扣减后的金额计算
-- 1) 订单记录活动归属（供结算分行与售后冲正口径）
ALTER TABLE orders
    ADD COLUMN promotion_id BIGINT UNSIGNED DEFAULT NULL COMMENT '命中的促销活动 id（v30）' AFTER coupon_scope,
    ADD COLUMN promotion_discount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '活动优惠金额（v30）' AFTER promotion_id,
    ADD COLUMN promotion_scope TINYINT NOT NULL DEFAULT 0 COMMENT '活动归属：0无活动 / 1平台活动 / 2本店活动（v30）' AFTER promotion_discount;

-- 2) 结算单补活动成本列（与券成本对称：平台活动平台补贴，本店活动店铺承担）
ALTER TABLE settlements
    ADD COLUMN promotion_cost_store DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '本店活动成本（v30）' AFTER coupon_cost_store,
    ADD COLUMN promotion_cost_platform DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '平台活动补贴（v30，平台承担）' AFTER promotion_cost_store;

-- 3) 活动命中索引：按状态 + 归属 + 门店 + 时间窗口检索进行中活动
ALTER TABLE promotions
    ADD KEY idx_promo_active (status, scope, store_id, start_time, end_time);
