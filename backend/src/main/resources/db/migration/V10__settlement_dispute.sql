-- v24 结算异议申诉 + 复核调整单
-- 1) 调整单关联原结算单（申诉复核后生成，type=3）
ALTER TABLE settlements
    ADD COLUMN parent_settlement_id BIGINT UNSIGNED NULL COMMENT '关联原结算单（调整单用）' AFTER payout_no;

-- 2) 店长补结算查看权限（设计矩阵：结算异议申诉 门店✅（本店）——店长可见本店结算单详情并提出异议）
INSERT INTO role_permissions (role_id, permission_id)
SELECT 4, 107
WHERE NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = 4 AND permission_id = 107);
