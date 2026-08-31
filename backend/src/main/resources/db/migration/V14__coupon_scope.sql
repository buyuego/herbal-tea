-- v28 优惠券全链路：券成本归属（本店券门店承担 / 平台券平台补贴）
-- 1) 订单记录用券归属，供结算按 scope 分行
ALTER TABLE orders
    ADD COLUMN coupon_scope TINYINT NOT NULL DEFAULT 0 COMMENT '券归属：0无券 / 1平台券 / 2本店券（v28）' AFTER coupon_amount;

-- 2) 结算单补平台券补贴列（与 points_cost_platform 对称；平台承担，不减店铺应付）
ALTER TABLE settlements
    ADD COLUMN coupon_cost_platform DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '平台券补贴（v28，平台承担）' AFTER coupon_cost_store;
