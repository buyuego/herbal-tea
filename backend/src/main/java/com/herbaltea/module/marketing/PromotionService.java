package com.herbaltea.module.marketing;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.marketing.dto.PromotionMatch;
import com.herbaltea.module.marketing.dto.PromotionQuery;
import com.herbaltea.module.marketing.dto.PromotionSaveRequest;
import com.herbaltea.module.marketing.dto.PromotionVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 促销活动服务（v30）
 */
public interface PromotionService {

    /** 活动分页 */
    IPage<PromotionVO> pagePromotions(PromotionQuery query);

    /** 活动详情 */
    PromotionVO getPromotion(Long id);

    /** 新建活动（草稿）；门店账号只能建本店活动并强制归属本店 */
    Long createPromotion(PromotionSaveRequest req, Long operatorStoreId);

    /** 编辑活动（仅草稿可编辑） */
    void updatePromotion(Long id, PromotionSaveRequest req, Long operatorStoreId);

    /** 发布：草稿 → 进行中（0→1） */
    void publishPromotion(Long id);

    /** 提前结束：进行中 → 已结束（1→2） */
    void stopPromotion(Long id);

    /** C 端：门店当前生效活动（平台活动 + 本店活动）；storeId 为空时仅返回平台活动 */
    List<PromotionVO> listActive(Long storeId);

    /**
     * 下单计价：匹配当前最优活动（不叠加，取优惠最大者）。
     *
     * @param storeId 业绩归属门店
     * @param amount  商品小计
     * @return 命中结果；无可用活动或优惠为 0 时返回 {@code null}
     */
    PromotionMatch matchBest(Long storeId, BigDecimal amount);

    /**
     * 按活动规则计算优惠金额（门槛按传入金额判定，结果按金额封顶）。
     *
     * @param promotion 活动实体
     * @param amount    订单商品小计
     * @return 优惠金额（未达门槛返回 0）
     */
    BigDecimal calcDiscount(com.herbaltea.module.marketing.entity.Promotion promotion, BigDecimal amount);

    /** 到点自动结束到期活动（定时任务调用），返回处理条数 */
    int closeExpired();
}
