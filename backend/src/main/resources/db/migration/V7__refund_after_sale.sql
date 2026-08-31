-- =====================================================================
-- V7：退款售后闭环（v20）——状态注释对齐 + 驳回审计列
--
-- 背景：V1 已建 refund_records / return_orders 表，但：
--   1. refund_records.status 的 DDL 注释是旧版 0-5，与权威状态机
--      RefundStatus（10待审批/20审批通过/30退款中/40已退款/50已驳回/
--      95回退失败-待人工，设计文档 11.3/D2/A3）不一致——仅修注释，不改类型
--   2. 退款驳回缺少审计列（原表只有 reason 申请原因，驳回原因需独立落库）
-- =====================================================================
ALTER TABLE refund_records
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 10
    COMMENT '状态机（RefundStatus）：10待审批/20审批通过/30退款中/40已退款/50已驳回/95回退失败-待人工';

ALTER TABLE refund_records
  ADD COLUMN reject_reason VARCHAR(255) DEFAULT NULL COMMENT '驳回原因（50 已驳回时填写）' AFTER reason,
  ADD COLUMN rejected_by   BIGINT UNSIGNED DEFAULT NULL COMMENT '驳回人（admin_users.id）' AFTER approved_at,
  ADD COLUMN rejected_at   DATETIME DEFAULT NULL COMMENT '驳回时间' AFTER rejected_by;
