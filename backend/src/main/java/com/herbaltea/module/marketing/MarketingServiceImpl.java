package com.herbaltea.module.marketing;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import com.herbaltea.infrastructure.outbox.OutboxPublisher;
import com.herbaltea.module.marketing.dto.PointRecordVO;
import com.herbaltea.module.marketing.entity.PointRecord;
import com.herbaltea.module.marketing.mapper.PointRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

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

    private final OutboxPublisher outboxPublisher;

    private final PointRecordMapper pointRecordMapper;

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
}
