package com.herbaltea.module.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.infrastructure.idempotency.IdempotencyService;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import com.herbaltea.infrastructure.outbox.OutboxPublisher;
import com.herbaltea.module.marketing.MarketingService;
import com.herbaltea.module.order.dto.CreateOrderRequest;
import com.herbaltea.module.order.dto.OrderCreateVO;
import com.herbaltea.module.order.dto.OrderDetailVO;
import com.herbaltea.module.order.dto.OrderItemVO;
import com.herbaltea.module.order.dto.OrderPageQuery;
import com.herbaltea.module.order.dto.ShipRequest;
import com.herbaltea.module.order.entity.Order;
import com.herbaltea.module.order.entity.OrderItem;
import com.herbaltea.module.order.entity.OrderShippingLog;
import com.herbaltea.module.order.entity.PaymentRecord;
import com.herbaltea.module.order.mapper.OrderItemMapper;
import com.herbaltea.module.order.mapper.OrderMapper;
import com.herbaltea.module.order.mapper.OrderShippingLogMapper;
import com.herbaltea.module.order.mapper.PaymentRecordMapper;
import com.herbaltea.module.product.ProductService;
import com.herbaltea.module.product.dto.SkuForSaleVO;
import com.herbaltea.module.user.entity.UserAddress;
import com.herbaltea.module.user.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单模块实现（对齐设计 v10 §16.4 / 16.11 / D1 / D10）
 *
 * <p>关键决策：
 * <ul>
 *   <li>下单价格来源：本店在售价（store_products），跨模块只读走 ProductService.getSkuForSale</li>
 *   <li>库存扣减/回滚：ProductService.deductStock/restoreStock（原子 SQL + 乐观锁，零超卖）</li>
 *   <li>状态流转：OrderStatus 枚举矩阵校验 + OrderMapper.casStatus 双条件更新（16.2）</li>
 *   <li>支付推进：10→20→30 同一事务完成（无独立"接单"动作，支付即待发货）</li>
 *   <li>超时关单幂等：CAS 失败即跳过（其他路径已处理）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 平台佣金比例默认 5%（下单时快照，V2 可配置项未落地前取默认） */
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.0500");

    /** 未支付关单窗口：30 分钟（16.12） */
    private static final long EXPIRE_MINUTES = 30;

    /** 单积分抵扣金额（v27：与结算 POINTS_COST_UNIT 同口径，1 积分 = 0.01 元） */
    private static final BigDecimal POINTS_DEDUCT_UNIT = new BigDecimal("0.01");

    /** 积分来源：门店营销（D15；平台活动积分=2 待营销活动落地后按活动判定） */
    private static final int POINTS_SOURCE_STORE = 1;

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderShippingLogMapper orderShippingLogMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final UserAddressMapper userAddressMapper;
    private final ProductService productService;
    private final MarketingService marketingService;
    private final OutboxPublisher outboxPublisher;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    // ==================== 下单 ====================

    @Override
    @Transactional
    public OrderCreateVO createOrder(Long userId, CreateOrderRequest req, String idempotencyKey) {
        // 1. 幂等键（24h 窗口，Redis SETNX；DB 唯一索引兜底 D1）
        if (!idempotencyService.tryAcquire(idempotencyKey)) {
            throw new BizException(ResultCode.IDEMPOTENT_REPLAY, "重复请求，请勿重复提交");
        }
        try {
            return doCreateOrder(userId, req);
        } catch (Exception e) {
            // 业务失败：本地事务回滚后释放键，允许客户端用同一键重试（防"假重放"）
            idempotencyService.release(idempotencyKey);
            throw e;
        }
    }

    private OrderCreateVO doCreateOrder(Long userId, CreateOrderRequest req) {

        // 2. SKU + 本店在售价（跨模块只读，模块边界约束）
        SkuForSaleVO sku = productService.getSkuForSale(req.skuId(), req.storeId());

        // 3. 收货地址快照（归属校验）
        UserAddress addr = userAddressMapper.selectById(req.addressId());
        if (addr == null || !addr.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "收货地址不存在");
        }

        // 4. 库存原子扣减（16.4 ③：stock>=qty + version 乐观锁，零超卖无锁）
        if (!productService.deductStock(req.skuId(), req.qty())) {
            throw new BizException("库存不足");
        }

        // 5. 金额计算（v27：积分抵扣已接入营销模块；优惠券待营销活动落地）
        BigDecimal unitPrice = sku.price();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(req.qty()));

        // 5.1 积分抵扣：订单号先生成（作为抵扣流水幂等键）→ 原子扣减 → 折算抵扣金额
        String orderNo = generateNo("HT");
        long usePoints = req.usePoints() == null ? 0L : Math.max(0, req.usePoints());
        BigDecimal pointsDeductAmount = BigDecimal.ZERO;
        if (usePoints > 0) {
            pointsDeductAmount = BigDecimal.valueOf(usePoints)
                    .multiply(POINTS_DEDUCT_UNIT)
                    .setScale(2, RoundingMode.HALF_UP);
            if (pointsDeductAmount.compareTo(subtotal) > 0) {
                throw new BizException("抵扣积分超过订单金额：" + usePoints + " 积分可抵 ¥"
                        + pointsDeductAmount + "，订单仅 ¥" + subtotal);
            }
            // 原子扣减（余额不足抛业务异常 → 下单事务整体回滚，库存一并还原）
            marketingService.usePoints(userId, (int) usePoints, orderNo);
        }
        BigDecimal payAmount = subtotal.subtract(pointsDeductAmount).max(BigDecimal.ZERO);
        // 赠送积分按「抵扣后实付」向下取整（1 元 = 1 积分，D15 规则复核）
        long pointsEarned = payAmount.setScale(0, RoundingMode.DOWN).longValue();

        // 6. 订单头
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStoreId(req.storeId());
        order.setStatus(Order.STATUS_PENDING_PAYMENT);
        order.setWarehouseStatus(Order.WH_READY);
        order.setTotalAmount(subtotal);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setPointsDeduct(usePoints);
        order.setPointsDeductAmount(pointsDeductAmount);
        order.setPointsEarned(pointsEarned);
        order.setPointsSource(POINTS_SOURCE_STORE);
        order.setPayAmount(payAmount);
        order.setCommissionRate(DEFAULT_COMMISSION_RATE);
        order.setReceiverName(addr.getReceiverName());
        order.setReceiverPhone(addr.getPhone());
        order.setReceiverAddress(addr.getProvince() + addr.getCity() + addr.getDistrict() + addr.getDetail());
        order.setRemark(req.remark());
        order.setExpireAt(LocalDateTime.now().plusMinutes(EXPIRE_MINUTES));
        order.setAutoCloseStatus(Order.AUTO_CLOSE_NONE);
        order.setUrgeCount(0);
        order.setShipTimeoutWarned(0);
        orderMapper.insert(order);

        // 7. 订单明细（全量快照）
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(sku.productId());
        item.setSkuId(sku.skuId());
        item.setName(sku.productName());
        item.setSpecs(sku.specs());
        item.setImage(sku.mainImage());
        item.setPrice(unitPrice);
        item.setQty(req.qty());
        item.setSubtotal(subtotal);
        orderItemMapper.insert(item);

        // 8. 支付单（待支付）
        PaymentRecord payRec = new PaymentRecord();
        payRec.setPayNo(generateNo("PAY"));
        payRec.setOrderId(order.getId());
        payRec.setAmount(payAmount);
        payRec.setStatus(PaymentRecord.STATUS_PENDING);
        paymentRecordMapper.insert(payRec);

        log.info("下单成功 orderNo={} userId={} storeId={} skuId={} qty={} pay={}",
                order.getOrderNo(), userId, req.storeId(), req.skuId(), req.qty(), payAmount);
        return new OrderCreateVO(order.getOrderNo(), payRec.getPayNo(), payAmount, order.getExpireAt());
    }

    @Override
    public OrderCreateVO createOrderForUser(CreateOrderRequest req, String idempotencyKey) {
        if (req.userId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "代客下单必须指定买家 userId");
        }
        return createOrder(req.userId(), req, idempotencyKey);
    }

    // ==================== 支付推进 ====================

    @Override
    @Transactional
    public void handlePaid(String outTradeNo, String transactionId) {
        PaymentRecord pay = paymentRecordMapper.selectByPayNo(outTradeNo);
        if (pay == null) {
            throw new BizException(ResultCode.NOT_FOUND, "支付单不存在: " + outTradeNo);
        }
        if (pay.getStatus() == PaymentRecord.STATUS_SUCCESS) {
            log.info("重复支付回调，幂等返回 payNo={}", outTradeNo);
            return;
        }
        Order order = orderMapper.selectById(pay.getOrderId());
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }

        // 10→20（乐观锁 CAS）
        int r1 = orderMapper.casStatus(order.getId(), Order.STATUS_PENDING_PAYMENT,
                Order.STATUS_PAID, order.getVersion());
        if (r1 == 0) {
            if (order.getStatus() == Order.STATUS_PAID
                    || order.getStatus() == Order.STATUS_PENDING_SHIPMENT) {
                log.info("订单已支付，幂等返回 orderNo={}", order.getOrderNo());
                return;
            }
            throw BizException.conflict("订单状态已变化，无法完成支付确认");
        }

        // 20→30 进入待发货（同一事务，状态机合法转移）
        Order fresh = orderMapper.selectById(order.getId());
        orderMapper.casStatus(fresh.getId(), Order.STATUS_PAID,
                Order.STATUS_PENDING_SHIPMENT, fresh.getVersion());

        // 回填：订单支付时间/仓库状态 + 支付单成功
        orderMapper.markPaid(order.getId());
        paymentRecordMapper.markPaid(pay.getId(), transactionId);

        // 发布 order_paid（订阅：营销积分 / 结算分账 / 发货任务，D10 同库事务）
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("userId", order.getUserId());
        payload.put("storeId", order.getStoreId());
        payload.put("payAmount", pay.getAmount());
        payload.put("pointsEarned", order.getPointsEarned());
        outboxPublisher.publish(OutboxEventType.order_paid,
                "order_paid:" + order.getOrderNo(), payload);

        log.info("支付确认成功 orderNo={} transactionId={}", order.getOrderNo(), transactionId);
    }

    // ==================== 状态推进 ====================

    @Override
    @Transactional
    public void ship(Long orderId, Long operatorAdminId, ShipRequest req) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        // 状态机校验（非法转移抛 40900）
        OrderStatus.of(order.getStatus()).transitTo(OrderStatus.SHIPPED);
        int r = orderMapper.casStatus(orderId, order.getStatus(),
                Order.STATUS_SHIPPED, order.getVersion());
        if (r == 0) {
            throw BizException.conflict("订单状态已变化，请刷新后重试");
        }
        orderMapper.markShipped(orderId, req.logisticsNo(), req.carrier(), operatorAdminId);

        // 物流轨迹
        OrderShippingLog sl = new OrderShippingLog();
        sl.setOrderId(orderId);
        sl.setStatus("已发货");
        sl.setTrackingNo(req.logisticsNo());
        sl.setCarrier(req.carrier());
        sl.setOperatorId(operatorAdminId);
        sl.setNote(req.note());
        orderShippingLogMapper.insert(sl);

        // order_shipped 事件（订阅：消息推送 / 签收超时计时）
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", order.getOrderNo());
        payload.put("trackingNo", req.logisticsNo());
        outboxPublisher.publish(OutboxEventType.order_shipped,
                "order_shipped:" + order.getOrderNo(), payload);

        log.info("发货成功 orderNo={} trackingNo={}", order.getOrderNo(), req.logisticsNo());
    }

    @Override
    @Transactional
    public void sign(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        OrderStatus.of(order.getStatus()).transitTo(OrderStatus.SIGNED);
        int r = orderMapper.casStatus(orderId, order.getStatus(),
                Order.STATUS_SIGNED, order.getVersion());
        if (r == 0) {
            throw BizException.conflict("订单状态已变化，请刷新后重试");
        }
        orderMapper.markSigned(orderId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", order.getOrderNo());
        payload.put("userId", userId);
        outboxPublisher.publish(OutboxEventType.order_auto_signed,
                "order_auto_signed:" + order.getOrderNo(), payload);

        log.info("签收成功 orderNo={} userId={}", order.getOrderNo(), userId);
    }

    @Override
    @Transactional
    public void cancel(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        OrderStatus.of(order.getStatus()).transitTo(OrderStatus.CLOSED);
        int r = orderMapper.casStatus(orderId, order.getStatus(),
                Order.STATUS_CLOSED, order.getVersion());
        if (r == 0) {
            throw BizException.conflict("订单状态已变化，请刷新后重试");
        }
        orderMapper.markAutoClose(orderId, Order.AUTO_CLOSE_USER);
        restoreItemsStock(orderId);
        publishClosedEvent(order, "用户取消");
    }

    @Override
    @Transactional
    public void closeExpired(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != Order.STATUS_PENDING_PAYMENT) {
            return; // 非待支付：已被其他路径处理，幂等跳过
        }
        int r = orderMapper.casStatus(orderId, Order.STATUS_PENDING_PAYMENT,
                Order.STATUS_CLOSED, order.getVersion());
        if (r == 0) {
            return; // 并发下已被处理
        }
        orderMapper.markAutoClose(orderId, Order.AUTO_CLOSE_TIMEOUT);
        restoreItemsStock(orderId);
        publishClosedEvent(order, "30 分钟未支付自动关单");
    }

    // ==================== 查询 ====================

    @Override
    public OrderDetailVO getOrderDetail(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
        PaymentRecord pay = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId).last("LIMIT 1"));

        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setStoreId(order.getStoreId());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.of(order.getStatus()).getDesc());
        vo.setWarehouseStatus(order.getWarehouseStatus());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setCouponAmount(order.getCouponAmount());
        vo.setPointsDeductAmount(order.getPointsDeductAmount());
        vo.setPointsEarned(order.getPointsEarned());
        vo.setPayAmount(order.getPayAmount());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setRemark(order.getRemark());
        vo.setTrackingNo(order.getTrackingNo());
        vo.setCarrier(order.getCarrier());
        vo.setPaidAt(order.getPaidAt());
        vo.setShippedAt(order.getShippedAt());
        vo.setFinishedAt(order.getFinishedAt());
        vo.setExpireAt(order.getExpireAt());
        vo.setCreatedAt(order.getCreatedAt());
        if (pay != null) {
            vo.setPayNo(pay.getPayNo());
            vo.setPayStatus(pay.getStatus());
        }
        vo.setItems(items.stream().map(it -> new OrderItemVO(
                it.getSkuId(), it.getName(), parseSpecs(it.getSpecs()),
                it.getImage(), it.getPrice(), it.getQty(), it.getSubtotal())).toList());
        return vo;
    }

    @Override
    public IPage<Order> pageOrders(OrderPageQuery query) {
        long size = Math.min(query.getSize(), 100);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(StringUtils.hasText(query.getOrderNo()), Order::getOrderNo, query.getOrderNo())
                .eq(query.getUserId() != null, Order::getUserId, query.getUserId())
                .eq(query.getStoreId() != null, Order::getStoreId, query.getStoreId())
                .eq(query.getStatus() != null, Order::getStatus, query.getStatus())
                .orderByDesc(Order::getId);
        return orderMapper.selectPage(new Page<>(query.getPage(), size), wrapper);
    }

    @Override
    public List<OrderShippingLog> shippingLogs(Long orderId) {
        return orderShippingLogMapper.selectList(new LambdaQueryWrapper<OrderShippingLog>()
                .eq(OrderShippingLog::getOrderId, orderId)
                .orderByAsc(OrderShippingLog::getId));
    }

    // ==================== 私有工具 ====================

    /** 库存回滚（按明细逐 SKU，关单/取消时调用，同库事务 D10） */
    private void restoreItemsStock(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
        for (OrderItem it : items) {
            productService.restoreStock(it.getSkuId(), it.getQty());
        }
    }

    private void publishClosedEvent(Order order, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("userId", order.getUserId());
        payload.put("reason", reason);
        outboxPublisher.publish(OutboxEventType.order_auto_closed,
                "order_auto_closed:" + order.getOrderNo(), payload);
    }

    /** 规格 JSON 解析（快照原样返回，解析失败兜底原始字符串） */
    private Object parseSpecs(String specs) {
        if (!StringUtils.hasText(specs)) {
            return null;
        }
        try {
            return objectMapper.readValue(specs, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return specs;
        }
    }

    /** 单号生成：前缀 + yyyyMMddHHmmss + 4 位随机（订单号/支付单号共用） */
    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
