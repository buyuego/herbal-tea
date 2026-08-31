package com.herbaltea.module.marketing;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import com.herbaltea.infrastructure.outbox.OutboxPublisher;
import com.herbaltea.module.marketing.dto.PointRecordVO;
import com.herbaltea.module.marketing.dto.PointsExpireBatch;
import com.herbaltea.module.marketing.dto.PointsExpireNotice;
import com.herbaltea.module.marketing.entity.PointRecord;
import com.herbaltea.module.marketing.entity.UserPointsAccount;
import com.herbaltea.module.marketing.mapper.PointRecordMapper;
import com.herbaltea.module.marketing.mapper.UserPointsAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 营销模块实现
 *
 * <p>积分体系（v27 落地，设计 D8 过期回收 / D15 双维归属）：
 * <ul>
 *   <li>发放 {@link #grantPoints}：幂等（biz_key=grant:{orderNo}）+ 账户 upsert + 流水，有效期 12 个月</li>
 *   <li>抵扣 {@link #usePoints}：{@code balance >= amount} 原子扣减，余额不足抛业务异常（下单事务回滚）</li>
 *   <li>回收 {@link #reclaimPoints}：退款时按订单回收已发未收积分，钳零不透支</li>
 *   <li>过期 {@link #expirePoints}：到期批次清零（change_type=4）+ 到期前 7 天提醒</li>
 * </ul>
 *
 * <p>待实现：优惠券（promotions / coupons / user_coupons）发放核销。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingServiceImpl implements MarketingService {

    /** 变动类型文案（与 PointRecord.TYPE_* 对齐） */
    private static final Map<Integer, String> CHANGE_TYPE_DESC = Map.of(
            PointRecord.TYPE_GRANT, "下单发放",
            PointRecord.TYPE_USE, "下单抵扣",
            PointRecord.TYPE_REFUND_RECLAIM, "退款回收",
            PointRecord.TYPE_EXPIRE, "过期清零",
            PointRecord.TYPE_SIGN_IN, "签到");

    /** 积分来源文案（D15 双维归属） */
    private static final Map<Integer, String> SOURCE_TYPE_DESC = Map.of(
            PointRecord.SOURCE_STORE, "门店营销",
            PointRecord.SOURCE_PLATFORM, "平台活动");

    /** 积分有效期（月）：设计 12.1，发放后 12 个月过期 */
    private static final int POINTS_EXPIRE_MONTHS = 12;

    /** 过期提醒提前量（天）：D8 */
    private static final int EXPIRE_NOTICE_DAYS = 7;

    /** 单次过期回收的批次上限，防止长事务 */
    private static final int EXPIRE_BATCH_LIMIT = 500;

    private final OutboxPublisher outboxPublisher;

    private final PointRecordMapper pointRecordMapper;

    private final UserPointsAccountMapper accountMapper;

    // ==================== 积分发放 ====================

    @Override
    @Transactional
    public void grantPoints(Long userId, Long storeId, Long orderId, String orderNo,
                            int amount, Integer sourceType) {
        if (userId == null || amount <= 0) {
            throw new BizException("发放积分数量必须大于 0");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException("发放积分缺少订单号（幂等键）");
        }
        int source = sourceType == null ? PointRecord.SOURCE_STORE : sourceType;
        if (source != PointRecord.SOURCE_STORE && source != PointRecord.SOURCE_PLATFORM) {
            throw new BizException("积分来源不合法：1门店营销 / 2平台活动");
        }

        String bizKey = "grant:" + orderNo;
        String batchNo = "B" + orderNo;

        // 幂等：uk_ptr_biz(change_type, biz_key) 命中即视为已发放（outbox 重投 / 重复消费）
        PointRecord dup = new PointRecord();
        dup.setUserId(userId);
        dup.setStoreId(source == PointRecord.SOURCE_PLATFORM ? null : storeId);
        dup.setOrderId(orderId);
        dup.setChangeType(PointRecord.TYPE_GRANT);
        dup.setSourceType(source);
        dup.setPoints((long) amount);
        dup.setBatchNo(batchNo);
        dup.setExpireAt(LocalDateTime.now().plusMonths(POINTS_EXPIRE_MONTHS));
        dup.setBizKey(bizKey);
        try {
            pointRecordMapper.insert(dup);
        } catch (DuplicateKeyException e) {
            log.info("积分发放幂等跳过 userId={} orderNo={} amount={}", userId, orderNo, amount);
            return;
        }

        // 账户累加（首次自动建户）
        accountMapper.upsertEarn(userId, (long) amount);
        log.info("发放积分 userId={} orderNo={} amount={} sourceType={} expireAt={}",
                userId, orderNo, amount, source, dup.getExpireAt());
    }

    // ==================== 积分抵扣 ====================

    @Override
    @Transactional
    public void usePoints(Long userId, int amount, String bizKey) {
        if (userId == null || amount <= 0) {
            throw new BizException("抵扣积分数量必须大于 0");
        }
        // 原子扣减：balance >= amount 才成功（16.4 同款，无锁零超扣）
        if (accountMapper.deductIfEnough(userId, (long) amount) == 0) {
            Long balance = currentBalance(userId);
            throw new BizException("积分余额不足：当前 " + (balance == null ? 0 : balance) + "，需 " + amount);
        }

        String key = StringUtils.hasText(bizKey) ? "use:" + bizKey : "use:" + UUID.randomUUID();
        PointRecord rec = new PointRecord();
        rec.setUserId(userId);
        rec.setChangeType(PointRecord.TYPE_USE);
        rec.setSourceType(PointRecord.SOURCE_STORE);
        rec.setPoints(-(long) amount);
        rec.setBizKey(key);
        try {
            pointRecordMapper.insert(rec);
        } catch (DuplicateKeyException e) {
            // 理论上不会走到（扣减已成功但流水重复），回滚事务避免重复抵扣
            throw BizException.conflict("该订单已抵扣过积分，请刷新后重试");
        }
        log.info("抵扣积分 userId={} amount={} bizKey={}", userId, amount, key);
    }

    // ==================== 退款回收 ====================

    @Override
    @Transactional
    public long reclaimPoints(Long userId, Long orderId, String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return 0;
        }
        long granted = pointRecordMapper.sumUnreclaimedByOrder(orderNo);
        if (granted <= 0) {
            log.info("退款回收跳过（无待回收积分）orderNo={}", orderNo);
            return 0;
        }

        // 账户扣减并钳零（用户可能已用掉部分积分，不透支为负）
        accountMapper.expireToBalance(userId, granted);

        PointRecord rec = new PointRecord();
        rec.setUserId(userId);
        rec.setOrderId(orderId);
        rec.setChangeType(PointRecord.TYPE_REFUND_RECLAIM);
        rec.setSourceType(PointRecord.SOURCE_STORE);
        rec.setPoints(-granted);
        rec.setBizKey("reclaim:" + orderNo);
        try {
            pointRecordMapper.insert(rec);
        } catch (DuplicateKeyException e) {
            log.info("退款回收幂等跳过 orderNo={}", orderNo);
            return 0;
        }
        log.info("退款回收积分 userId={} orderNo={} points={}", userId, orderNo, granted);
        return granted;
    }

    // ==================== 过期回收 ====================

    @Override
    @Transactional
    public int expirePoints() {
        LocalDateTime now = LocalDateTime.now();

        // 1) 到期前 7 天提醒（D8：订阅消息，交由 outbox 异步推送）
        List<PointsExpireNotice> notices =
                pointRecordMapper.selectExpiringUsers(now, now.plusDays(EXPIRE_NOTICE_DAYS));
        for (PointsExpireNotice n : notices) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", n.getUserId());
            payload.put("points", n.getPoints());
            payload.put("expireAt", now.plusDays(EXPIRE_NOTICE_DAYS).toString());
            outboxPublisher.publish(OutboxEventType.points_expired,
                    "points_expire_notice:" + n.getUserId() + ":" + now.toLocalDate(), payload);
        }

        // 2) 到期批次清零
        List<PointsExpireBatch> batches = pointRecordMapper.selectExpireBatches(now);
        int handled = 0;
        for (PointsExpireBatch b : batches) {
            if (handled >= EXPIRE_BATCH_LIMIT) {
                log.warn("[积分过期] 本次达到批次上限 {}，剩余下轮处理", EXPIRE_BATCH_LIMIT);
                break;
            }
            // 账户扣减（钳零，用户可能已提前用掉）
            accountMapper.expireToBalance(b.getUserId(), b.getPoints());

            PointRecord rec = new PointRecord();
            rec.setUserId(b.getUserId());
            rec.setChangeType(PointRecord.TYPE_EXPIRE);
            rec.setSourceType(PointRecord.SOURCE_STORE);
            rec.setPoints(-b.getPoints());
            rec.setBatchNo(b.getBatchNo());
            rec.setBizKey("expire:" + b.getBatchNo());
            try {
                pointRecordMapper.insert(rec);
            } catch (DuplicateKeyException e) {
                // 并发下已回收，跳过（幂等）
                continue;
            }
            handled++;
        }

        if (handled > 0 || !notices.isEmpty()) {
            log.info("[积分过期] 清零批次 {} 个 / 到期提醒 {} 人", handled, notices.size());
        }
        return handled;
    }

    // ==================== 查询 ====================

    @Override
    public IPage<PointRecordVO> pagePointRecords(Long userId, Integer changeType, long page, long size) {
        if (changeType != null && !CHANGE_TYPE_DESC.containsKey(changeType)) {
            throw new BizException("积分变动类型不合法：1发放 / 2抵扣 / 3退款回收 / 4过期清零 / 5签到");
        }
        long p = page <= 0 ? 1 : page;
        long s = Math.min(size <= 0 ? 10 : size, 100);
        IPage<PointRecordVO> result = pointRecordMapper.pageByUser(new Page<>(p, s), userId, changeType);
        // 文案由服务端填充，前端直接展示
        result.getRecords().forEach(r -> {
            r.setChangeTypeDesc(CHANGE_TYPE_DESC.getOrDefault(r.getChangeType(), "#" + r.getChangeType()));
            r.setSourceTypeDesc(SOURCE_TYPE_DESC.getOrDefault(r.getSourceType(), "#" + r.getSourceType()));
        });
        return result;
    }

    /** 当前可用积分（无账户记 0） */
    private Long currentBalance(Long userId) {
        UserPointsAccount acc = accountMapper.selectByUserId(userId);
        return acc == null ? 0L : acc.getBalance();
    }
}
