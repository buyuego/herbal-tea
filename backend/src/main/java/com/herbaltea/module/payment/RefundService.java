package com.herbaltea.module.payment;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.payment.dto.InspectRequest;
import com.herbaltea.module.payment.dto.RefundDetailVO;
import com.herbaltea.module.payment.dto.RefundPageQuery;
import com.herbaltea.module.payment.dto.RefundPageVO;

/**
 * 退款售后业务（refund_records / return_orders）
 *
 * <p>链路（设计文档 11.3 / 16.11）：
 * 申请（refund:submit，门店/总部）→ 审批（refund:approve，10→20/50）→
 * 未发货/在途直退（20→30→40）或 已签收退货验货（return:inspect，
 * 验货通过 20→30→40 / 不通过 20→50）→ 订单 60退款中 → 80已退款。
 *
 * <p>微信真实退款（服务商分账回退）依赖外部支付通道，由 PaymentService
 * 预留；本服务在 dev 环境以模拟推进完成全链路（与 MockPayController 同思路）。
 */
public interface RefundService {

    /**
     * 退款申请（门店/总部对已支付订单发起，refund:submit）
     *
     * <p>订单 20/30/40/50 → 60 退款中；按订单阶段判定退款分支
     * （1 未发货直退 / 2 在途拦截 / 3 已签收退货）；分支 3 同步建退货单。
     *
     * @return 退款单 id
     */
    Long applyRefund(Long orderId, Long operatorAdminId, String reason);

    /**
     * 审批通过（refund:approve）：10 → 20，回填审批审计；
     * 分支 1/2 直接走退款（20→30→40 + 订单 80），分支 3 等待退货验货。
     */
    void approveRefund(Long refundId, Long approverAdminId);

    /**
     * 驳回（refund:approve）：10 → 50，回填驳回审计；
     * 订单 60 退款中按分支恢复原阶段（1→30 / 2→40 / 3→50），退货单作废。
     */
    void rejectRefund(Long refundId, Long approverAdminId, String reason);

    /**
     * 总部收货（return:inspect，总部仓管）：退货单 warehouse_status 1 待收货 → 2 已收货、
     * 退货单 1 待寄回 → 3 待验货，回填收货人与收货时间；
     * 收货后（warehouse_status=2）才允许验货。
     */
    void receiveReturn(Long refundId, Long receiverAdminId);

    /**
     * 退货验货（return:inspect，总部仓管）：退货单已收货（warehouse_status=2）才可执行；
     * 验货通过（完好/破损部分退）→ 退款 20→30→40 + 订单 80；
     * 验货不通过（非质量问题拒退）→ 退款 20→50，订单恢复 50 已签收。
     */
    void inspectReturn(Long refundId, Long inspectorAdminId, InspectRequest req);

    /**
     * 退款单分页（menu:refund）。门店管理员强制本店范围（storeIds 注入），
     * 总部可全量并按 storeId 筛选。
     */
    IPage<RefundPageVO> pageRefunds(RefundPageQuery query);

    /**
     * 退款单详情（menu:refund）：退款单 + 订单头 + 退货单（分支 3 时有值）。
     */
    RefundDetailVO detailRefund(Long refundId);
}
