-- =====================================================================
-- V9 结算模块：settlements.status 注释对齐权威状态机（10/20/30/40/90）
-- SettlementStatus 枚举（module.settlement）为权威定义：
--   10 待确认 → 20 平台审核 → 30 已结算 → 40 已打款 / 90 已冲正
-- V1 DDL 的 0-4 旧注释与 Java 状态机不一致，本轮对齐（同 V7 refund 手法）
-- =====================================================================

ALTER TABLE settlements
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 10 COMMENT '10待确认/20平台审核/30已结算/40已打款/90已冲正（CAS 乐观锁更新）';

ALTER TABLE settlements
  MODIFY COLUMN confirm_status TINYINT NOT NULL DEFAULT 0 COMMENT '0待确认/1自动确认/2人工确认/3有异议（店铺确认维度，与 status 并行）';
