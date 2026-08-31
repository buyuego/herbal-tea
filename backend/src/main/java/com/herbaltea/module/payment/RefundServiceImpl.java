package com.herbaltea.module.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import com.herbaltea.infrastructure.outbox.OutboxPublisher;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.order.OrderStatus;
import com.herbaltea.module.order.entity.Order;
import com.herbaltea.module.order.mapper.OrderMapper;
import com.herbaltea.module.order.mapper.PaymentRecordMapper;
import com.herbaltea.module.payment.dto.InspectRequest;
import com.herbaltea.module.payment.dto.RefundDetailVO;
import com.herbaltea.module.payment.dto.RefundPageQuery;
import com.herbaltea.module.payment.dto.RefundPageVO;
import com.herbaltea.module.payment.entity.RefundRecord;
import com.herbaltea.module.payment.entity.ReturnOrder;
import com.herbaltea.module.payment.mapper.RefundRecordMapper;
import com.herbaltea.module.payment.mapper.ReturnOrderMapper;
import com.herbaltea.module.store.entity.Store;
import com.herbaltea.module.store.mapper.StoreMapper;
import com.herbaltea.module.user.entity.User;
import com.herbaltea.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 退款售后业务实现（v20）
 *
 * <p>核心原则：
 * <ul>
 *   <li>状态流转一律 CAS(status, version) 双条件更新（16.2），冲突抛 40900</li>
 *   <li>退款分支在申请时同事务判定（1 未发货直退 / 2 在途拦截 / 3 已签收退货）</li>
 *   <li>签收退货：一张退款单只建一张退货单（uk_ro_refund 兜底，D1），
 *       验货通过才推进退款，验货不通过退款驳回、订单恢复已签收</li>
 *   <li>门店数据范围：STORE 管理员强制本店（storeIds 注入 SQL / 归属校验），总部全量</li>
 *   <li>微信真实退款（服务商分账回退）属外部资金通道，dev 以模拟推进完成全链路</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 总部退货寄回地址（系统给出，退货单快照） */
    private static final String RETURN_ADDRESS = "总部售后仓：杭州市余杭区文一西路 969 号 1 号仓（养生茶售后收）";

    /** 验货结论：完好退全款（通过） */
    private static final String INSPECT_PASS_INTACT = "完好退全款";
    /** 验货结论：破损部分退款（通过，可按指定金额部分退） */
    private static final String INSPECT_PASS_PARTIAL = "破损部分退款";
    /** 验货结论：非质量问题拒退（不通过） */
    private static final String INSPECT_REJECT = "非质量问题拒退";

    private final RefundRecordMapper refundRecordMapper;
    private final ReturnOrderMapper returnOrderMapper;
    private final OrderMapper orderMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final StoreMapper storeMapper;
    private final UserMapper userMapper;
    private final OutboxPublisher outboxPublisher;

    // ==================== 写链路 ====================

    @Override
    @Transactional
    public Long applyRefund(Long orderId, Long operatorAdminId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        checkStoreAccess(order.getStoreId());

        // 订单状态机校验：20 已支付 / 30 待发货 / 40 已发货 / 50 已签收 可申请退款
        OrderStatus cur = OrderStatus.of(order.getStatus());
        boolean refundable = cur == OrderStatus.PAID
                || cur == OrderStatus.PENDING_SHIPMENT
                || cur == OrderStatus.SHIPPED
                || cur == OrderStatus.SIGNED;
        if (!refundable) {
            throw new BizException("订单当前状态（" + cur.getDesc() + "）不可申请退款");
        }

        // 订单 → 60 退款中（CAS）
        int r = orderMapper.casStatus(orderId, order.getStatus(),
                Order.STATUS_REFUNDING, order.getVersion());
        if (r == 0) {
            throw BizException.conflict("订单状态已变化，请刷新后重试");
        }

        // 退款分支判定（申请时同事务，16.11）
        int branch;
        if (cur == OrderStatus.SHIPPED) {
            branch = RefundRecord.BRANCH_IN_TRANSIT;      // 2 在途拦截
        } else if (cur == OrderStatus.SIGNED) {
            branch = RefundRecord.BRANCH_RETURNED;        // 3 已签收退货
        } else {
            branch = RefundRecord.BRANCH_NOT_SHIPPED;     // 1 未发货直退
        }

        // 退款单（10 待审批）
        RefundRecord rr = new RefundRecord();
        rr.setRefundNo(generateNo("RF"));
        rr.setOrderId(orderId);
        rr.setUserId(order.getUserId());
        rr.setAmount(order.getPayAmount());
        rr.setReason(reason);
        rr.setRefundBranch(branch);
        rr.setStatus(RefundRecord.STATUS_PENDING_APPROVAL);
        rr.setEscalationStatus(RefundRecord.ESCALATION_NORMAL);
        refundRecordMapper.insert(rr);

        // 已签收退货 → 建退货单（待寄回，warehouse_status 待收货）
        if (branch == RefundRecord.BRANCH_RETURNED) {
            ReturnOrder ro = new ReturnOrder();
            ro.setRefundId(rr.getId());
            ro.setOrderId(orderId);
            ro.setStoreId(order.getStoreId());
            ro.setBranch(branch);
            ro.setReturnAddress(RETURN_ADDRESS);
            ro.setWarehouseStatus(ReturnOrder.WH_TO_RECEIVE);
            ro.setStatus(ReturnOrder.STATUS_TO_RETURN);
            returnOrderMapper.insert(ro);
        }

        log.info("退款申请成功 refundNo={} orderNo={} branch={} operator={}",
                rr.getRefundNo(), order.getOrderNo(), branch, operatorAdminId);
        return rr.getId();
    }

    @Override
    @Transactional
    public void approveRefund(Long refundId, Long approverAdminId) {
        RefundRecord rr = requireRefund(refundId);
        Order order = requireOrder(rr.getOrderId());
        checkStoreAccess(order.getStoreId());

        // 10 → 20（状态机 + CAS）
        checkTransition(rr, RefundStatus.APPROVED);
        int r = refundRecordMapper.casStatus(refundId, rr.getStatus(),
                RefundRecord.STATUS_APPROVED, rr.getVersion());
        if (r == 0) {
            throw BizException.conflict("退款单状态已变化，请刷新后重试");
        }

        // 回填审批审计：门店管理员=门店审批，总部=总部兜底
        UserContext ctx = UserContext.get();
        int level = (ctx != null && ctx.getStoreId() != null && ctx.getStoreId() > 0)
                ? RefundRecord.APPROVED_BY_STORE : RefundRecord.APPROVED_BY_HQ;
        refundRecordMapper.markApproved(refundId, level, approverAdminId);
        orderMapper.markRefundApproved(order.getId(), approverAdminId);

        // 退款审批通过事件（订阅：结算冲正）
        publishRefundEvent(OutboxEventType.refund_approved, rr);

        // 分支 1/2：无退货，直接走退款；分支 3：等待退货验货
        if (rr.getRefundBranch() != RefundRecord.BRANCH_RETURNED) {
            completeRefund(rr, rr.getAmount());
        }
        log.info("退款审批通过 refundNo={} orderNo={} level={} approver={}",
                rr.getRefundNo(), order.getOrderNo(), level, approverAdminId);
    }

    @Override
    @Transactional
    public void rejectRefund(Long refundId, Long approverAdminId, String reason) {
        RefundRecord rr = requireRefund(refundId);
        Order order = requireOrder(rr.getOrderId());
        checkStoreAccess(order.getStoreId());

        // 10 → 50（状态机 + CAS）
        checkTransition(rr, RefundStatus.REJECTED);
        int r = refundRecordMapper.casStatus(refundId, rr.getStatus(),
                RefundRecord.STATUS_REJECTED, rr.getVersion());
        if (r == 0) {
            throw BizException.conflict("退款单状态已变化，请刷新后重试");
        }
        refundRecordMapper.markRejected(refundId, approverAdminId, reason);

        // 订单 60 退款中 → 按分支恢复原阶段（1→30 待发货 / 2→40 已发货 / 3→50 已签收）
        int restore = switch (rr.getRefundBranch()) {
            case RefundRecord.BRANCH_IN_TRANSIT -> Order.STATUS_SHIPPED;
            case RefundRecord.BRANCH_RETURNED -> Order.STATUS_SIGNED;
            default -> Order.STATUS_PENDING_SHIPMENT;
        };
        int ro = orderMapper.casStatus(order.getId(), Order.STATUS_REFUNDING, restore, order.getVersion());
        if (ro == 0) {
            log.warn("退款驳回时订单状态非退款中 refundNo={} orderId={} curStatus={}",
                    rr.getRefundNo(), order.getId(), order.getStatus());
        }

        // 分支 3：退货单作废（未完结时置 5 已取消）
        if (rr.getRefundBranch() == RefundRecord.BRANCH_RETURNED) {
            cancelReturnOrder(rr.getId());
        }
        log.info("退款驳回 refundNo={} orderNo={} reason={} operator={}",
                rr.getRefundNo(), order.getOrderNo(), reason, approverAdminId);
    }

    @Override
    @Transactional
    public void receiveReturn(Long refundId, Long receiverAdminId) {
        ReturnOrder ro = returnOrderMapper.selectOne(new LambdaQueryWrapper<ReturnOrder>()
                .eq(ReturnOrder::getRefundId, refundId).last("LIMIT 1"));
        if (ro == null) {
            throw new BizException(ResultCode.NOT_FOUND, "退货单不存在（该退款单非签收退货链路）");
        }
        Order order = requireOrder(ro.getOrderId());
        checkStoreAccess(order.getStoreId());

        // 1 待收货 → 2 已收货（CAS，防重复收货）
        int r = returnOrderMapper.casReceive(ro.getId(), receiverAdminId);
        if (r == 0) {
            throw BizException.conflict("退货单已收货或状态已变化，请刷新后重试");
        }
        log.info("总部收货 refundId={} orderNo={} receiver={}",
                refundId, order.getOrderNo(), receiverAdminId);
    }

    @Override
    @Transactional
    public void inspectReturn(Long refundId, Long inspectorAdminId, InspectRequest req) {
        ReturnOrder ro = returnOrderMapper.selectOne(new LambdaQueryWrapper<ReturnOrder>()
                .eq(ReturnOrder::getRefundId, refundId).last("LIMIT 1"));
        if (ro == null) {
            throw new BizException(ResultCode.NOT_FOUND, "退货单不存在（该退款单非签收退货链路）");
        }
        Order order = requireOrder(ro.getOrderId());
        checkStoreAccess(order.getStoreId());

        if (ro.getWarehouseStatus() != ReturnOrder.WH_RECEIVED) {
            throw new BizException("退货单当前状态（" + whStatusDesc(ro.getWarehouseStatus()) + "），须总部收货后方可验货");
        }
        RefundRecord rr = requireRefund(refundId);
        if (rr.getStatus() != RefundRecord.STATUS_APPROVED) {
            throw new BizException("退款单未审批通过（当前：" + RefundStatus.of(rr.getStatus()).getDesc() + "），不可验货");
        }

        boolean passed = isInspectPassed(req.getResult());
        int r = returnOrderMapper.casInspect(ro.getId(),
                passed ? ReturnOrder.WH_PASSED : ReturnOrder.WH_FAILED,
                req.getResult(), inspectorAdminId);
        if (r == 0) {
            throw BizException.conflict("退货单已验货或状态已变化，请刷新后重试");
        }

        if (passed) {
            // 破损部分退款：按指定金额（须 0 < 金额 ≤ 原退款额）；否则全额
            BigDecimal amount = rr.getAmount();
            if (req.getRefundAmount() != null) {
                if (req.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0
                        || req.getRefundAmount().compareTo(rr.getAmount()) > 0) {
                    throw new BizException("部分退款金额须大于 0 且不超过原退款金额");
                }
                amount = req.getRefundAmount();
            }
            completeRefund(rr, amount);
        } else {
            // 验货不通过（非质量问题拒退）→ 退款 20 → 50 驳回，订单恢复 50 已签收
            checkTransition(rr, RefundStatus.REJECTED);
            refundRecordMapper.casStatus(rr.getId(), rr.getStatus(),
                    RefundRecord.STATUS_REJECTED, rr.getVersion());
            refundRecordMapper.markRejected(rr.getId(), inspectorAdminId, "验货不通过：" + req.getResult());
            orderMapper.casStatus(order.getId(), Order.STATUS_REFUNDING,
                    Order.STATUS_SIGNED, order.getVersion());
        }
        log.info("退货验货 refundNo={} result={} passed={} inspector={}",
                rr.getRefundNo(), req.getResult(), passed, inspectorAdminId);
    }

    // ==================== 读链路 ====================

    @Override
    public IPage<RefundPageVO> pageRefunds(RefundPageQuery query) {
        long size = Math.min(query.getSize(), 100);
        List<Long> storeIds = null;
        UserContext ctx = UserContext.get();
        if (ctx != null && "STORE".equals(ctx.getDataScope())) {
            // 门店管理员强制本店范围（Data Scope 拦截器语义），忽略传入 storeId
            storeIds = (ctx.getStoreIds() != null && !ctx.getStoreIds().isEmpty())
                    ? ctx.getStoreIds() : List.of(ctx.getStoreId());
        }
        IPage<RefundPageVO> page = refundRecordMapper.pageRefunds(
                new Page<>(query.getPage(), size), query, storeIds);
        page.getRecords().forEach(this::fillDesc);
        return page;
    }

    @Override
    public RefundDetailVO detailRefund(Long refundId) {
        RefundRecord rr = requireRefund(refundId);
        Order order = requireOrder(rr.getOrderId());
        checkStoreAccess(order.getStoreId());

        RefundDetailVO vo = new RefundDetailVO();
        // 退款单
        vo.setId(rr.getId());
        vo.setRefundNo(rr.getRefundNo());
        vo.setOrderId(rr.getOrderId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStoreId(order.getStoreId());
        vo.setAmount(rr.getAmount());
        vo.setReason(rr.getReason());
        vo.setRefundBranch(rr.getRefundBranch());
        vo.setRefundBranchDesc(branchDesc(rr.getRefundBranch()));
        vo.setStatus(rr.getStatus());
        vo.setStatusDesc(RefundStatus.of(rr.getStatus()).getDesc());
        vo.setEscalationStatus(rr.getEscalationStatus());
        vo.setApprovedByLevel(rr.getApprovedByLevel());
        vo.setApprovedBy(rr.getApprovedBy());
        vo.setApprovedAt(rr.getApprovedAt());
        vo.setRejectReason(rr.getRejectReason());
        vo.setRejectedAt(rr.getRejectedAt());
        vo.setHandledAt(rr.getHandledAt());
        vo.setCreatedAt(rr.getCreatedAt());
        // 订单头
        Store store = storeMapper.selectById(order.getStoreId());
        vo.setStoreName(store != null ? store.getStoreName() : null);
        User user = userMapper.selectById(rr.getUserId());
        vo.setUserName(user != null ? user.getNickname() : null);
        vo.setUserPhone(user != null ? user.getPhone() : null);
        vo.setPayAmount(order.getPayAmount());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setPaidAt(order.getPaidAt());
        vo.setOrderWarehouseStatus(order.getWarehouseStatus());
        vo.setOrderWarehouseStatusDesc(warehouseDesc(order.getWarehouseStatus()));
        // 退货单（分支 3）
        ReturnOrder ro = returnOrderMapper.selectOne(new LambdaQueryWrapper<ReturnOrder>()
                .eq(ReturnOrder::getRefundId, refundId).last("LIMIT 1"));
        if (ro != null) {
            vo.setReturnId(ro.getId());
            vo.setReturnStatus(ro.getStatus());
            vo.setReturnStatusDesc(returnStatusDesc(ro.getStatus()));
            vo.setReturnAddress(ro.getReturnAddress());
            vo.setReturnTrackingNo(ro.getReturnTrackingNo());
            vo.setReturnCarrier(ro.getReturnCarrier());
            vo.setWarehouseStatus(ro.getWarehouseStatus());
            vo.setWarehouseStatusDesc(whStatusDesc(ro.getWarehouseStatus()));
            vo.setInspectionResult(ro.getInspectionResult());
            vo.setInspectedBy(ro.getInspectedBy());
            vo.setReceivedBy(ro.getReceivedBy());
            vo.setReceivedAt(ro.getReceivedAt());
        }
        return vo;
    }

    // ==================== 内部方法 ====================

    /** 完成退款：20→30→40（dev mock 微信退款）+ 订单 60→80 + 支付单置已退款 */
    private void completeRefund(RefundRecord rr, BigDecimal amount) {
        // 20 → 30 退款中（CAS）
        RefundRecord fresh = refundRecordMapper.selectById(rr.getId());
        int r1 = refundRecordMapper.casStatus(fresh.getId(), RefundRecord.STATUS_APPROVED,
                RefundRecord.STATUS_REFUNDING, fresh.getVersion());
        if (r1 == 0) {
            if (fresh.getStatus() == RefundRecord.STATUS_REFUNDED) {
                log.info("退款已完结，幂等返回 refundNo={}", rr.getRefundNo());
                return;
            }
            throw BizException.conflict("退款单状态已变化，请刷新后重试");
        }
        // TODO: 真实链路调用微信退款 API（服务商分账回退，WxPayService.refund），
        //       回调推进 30→40；dev 环境直接模拟退款成功
        refundRecordMapper.casStatus(fresh.getId(), RefundRecord.STATUS_REFUNDING,
                RefundRecord.STATUS_REFUNDED, fresh.getVersion() + 1);
        refundRecordMapper.markHandled(fresh.getId());

        // 订单 60 退款中 → 80 已退款
        Order order = orderMapper.selectById(rr.getOrderId());
        int ro = orderMapper.casStatus(order.getId(), Order.STATUS_REFUNDING,
                Order.STATUS_REFUNDED, order.getVersion());
        if (ro == 0) {
            log.warn("退款完成时订单状态非退款中 refundNo={} orderId={} curStatus={}",
                    rr.getRefundNo(), order.getId(), order.getStatus());
        }
        // 支付单置已退款（幂等：仅 1成功 → 3已退款）
        paymentRecordMapper.markRefundedByOrder(rr.getOrderId());
        log.info("退款完成 refundNo={} orderNo={} amount={}", rr.getRefundNo(), order.getOrderNo(), amount);
    }

    private void checkTransition(RefundRecord rr, RefundStatus target) {
        try {
            RefundStatus.of(rr.getStatus()).transitTo(target);
        } catch (IllegalArgumentException e) {
            throw new BizException("退款单当前状态（" + RefundStatus.of(rr.getStatus()).getDesc()
                    + "）不允许该操作");
        }
    }

    /** 门店数据范围校验：STORE 管理员只能操作本店订单的退款单 */
    private void checkStoreAccess(Long orderStoreId) {
        UserContext ctx = UserContext.get();
        if (ctx != null && "STORE".equals(ctx.getDataScope())) {
            List<Long> sids = (ctx.getStoreIds() != null && !ctx.getStoreIds().isEmpty())
                    ? ctx.getStoreIds() : List.of(ctx.getStoreId());
            if (!sids.contains(orderStoreId)) {
                throw new BizException(ResultCode.FORBIDDEN, "无权操作其他门店的退款单");
            }
        }
    }

    private void cancelReturnOrder(Long refundId) {
        ReturnOrder ro = returnOrderMapper.selectOne(new LambdaQueryWrapper<ReturnOrder>()
                .eq(ReturnOrder::getRefundId, refundId).last("LIMIT 1"));
        if (ro != null && ro.getStatus() != ReturnOrder.STATUS_DONE) {
            ro.setStatus(ReturnOrder.STATUS_CANCELED);
            returnOrderMapper.updateById(ro);
        }
    }

    private void publishRefundEvent(OutboxEventType type, RefundRecord rr) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("refundId", rr.getId());
        payload.put("refundNo", rr.getRefundNo());
        payload.put("orderId", rr.getOrderId());
        payload.put("amount", rr.getAmount());
        payload.put("refundBranch", rr.getRefundBranch());
        outboxPublisher.publish(type, "refund_approved:" + rr.getRefundNo(), payload);
    }

    private RefundRecord requireRefund(Long refundId) {
        RefundRecord rr = refundRecordMapper.selectById(refundId);
        if (rr == null) {
            throw new BizException(ResultCode.NOT_FOUND, "退款单不存在");
        }
        return rr;
    }

    private Order requireOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "关联订单不存在");
        }
        return order;
    }

    private void fillDesc(RefundPageVO vo) {
        vo.setRefundBranchDesc(branchDesc(vo.getRefundBranch()));
        vo.setStatusDesc(RefundStatus.of(vo.getStatus()).getDesc());
        if (vo.getReturnStatus() != null) {
            vo.setReturnStatusDesc(returnStatusDesc(vo.getReturnStatus()));
        }
        if (vo.getWarehouseStatus() != null) {
            vo.setWarehouseStatusDesc(whStatusDesc(vo.getWarehouseStatus()));
        }
    }

    private static boolean isInspectPassed(String result) {
        return INSPECT_PASS_INTACT.equals(result) || INSPECT_PASS_PARTIAL.equals(result);
    }

    private static String branchDesc(Integer branch) {
        return switch (branch == null ? 0 : branch) {
            case RefundRecord.BRANCH_NOT_SHIPPED -> "未发货直退";
            case RefundRecord.BRANCH_IN_TRANSIT -> "在途拦截";
            case RefundRecord.BRANCH_RETURNED -> "已签收退货";
            default -> "-";
        };
    }

    private static String returnStatusDesc(Integer status) {
        return switch (status == null ? -1 : status) {
            case ReturnOrder.STATUS_APPLYING -> "申请中";
            case ReturnOrder.STATUS_TO_RETURN -> "待寄回";
            case ReturnOrder.STATUS_IN_TRANSIT -> "在途";
            case ReturnOrder.STATUS_TO_INSPECT -> "待验货";
            case ReturnOrder.STATUS_DONE -> "已完结";
            case ReturnOrder.STATUS_CANCELED -> "已取消";
            default -> "-";
        };
    }

    private static String whStatusDesc(Integer status) {
        return switch (status == null ? -1 : status) {
            case ReturnOrder.WH_TO_RECEIVE -> "待收货";
            case ReturnOrder.WH_RECEIVED -> "已收货";
            case ReturnOrder.WH_PASSED -> "验货通过";
            case ReturnOrder.WH_FAILED -> "验货不通过";
            default -> "-";
        };
    }

    /** 订单 warehouse_status 描述（1待接单/2待发货/3已发货/4已签收） */
    private static String warehouseDesc(Integer status) {
        return switch (status == null ? -1 : status) {
            case Order.WH_READY -> "待接单";
            case Order.WH_TO_SHIP -> "待发货";
            case Order.WH_SHIPPED -> "已发货";
            case Order.WH_SIGNED -> "已签收";
            default -> "-";
        };
    }

    /** 单号生成：前缀 + yyyyMMddHHmmss + 4 位随机（退款单号） */
    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
