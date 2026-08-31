package com.herbaltea.module.marketing;

import com.herbaltea.infrastructure.outbox.OutboxEventType;
import com.herbaltea.infrastructure.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 营销模块骨架实现
 *
 * <p>待实现：
 * <ol>
 *   <li>grantPoints：积分发放双维归属（D15）——source_type=1 门店营销（成本归门店）/
 *       =2 平台活动（平台补贴），point_records 记 source_type，结算单按来源分行</li>
 *   <li>expirePoints：每日过期回收（points.expired 事件，D8）</li>
 *   <li>优惠券（promotions/coupons/user_coupons）：满减券发放核销</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingServiceImpl implements MarketingService {

    private final OutboxPublisher outboxPublisher;

    @Override
    public void grantPoints(Long userId, Long storeId, Long orderId, int amount, Integer sourceType) {
        // TODO: 积分事务：user_points_accounts 增加 + point_records 记录（source_type，D15）
        //       结算单展示由结算模块按 point_records 聚合（1=门店积分行 / 2=平台补贴行）
        log.info("[骨架] 发放积分 userId={} orderId={} amount={} sourceType={}", userId, orderId, amount, sourceType);
    }

    @Override
    public void expirePoints() {
        // TODO: 扫描即将过期积分 → 清零入流水 + 到期前 7 天订阅消息提醒（D8）
        //       每日执行，发布 points.expired
        outboxPublisher.publish(OutboxEventType.points_expired, "expire:daily", null);
    }

    @Override
    public void usePoints(Long userId, int amount) {
        // TODO: UPDATE user_points_accounts SET balance = balance - #{amount}
        //       WHERE user_id = ? AND balance >= #{amount}（原子扣减，16.4 同款）
    }
}
