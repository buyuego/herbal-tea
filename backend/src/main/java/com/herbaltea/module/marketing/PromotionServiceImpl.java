package com.herbaltea.module.marketing;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.module.marketing.dto.PromotionMatch;
import com.herbaltea.module.marketing.dto.PromotionQuery;
import com.herbaltea.module.marketing.dto.PromotionSaveRequest;
import com.herbaltea.module.marketing.dto.PromotionVO;
import com.herbaltea.module.marketing.entity.Promotion;
import com.herbaltea.module.marketing.mapper.PromotionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 促销活动服务实现（v30）
 *
 * <p>核心约定：
 * <ul>
 *   <li><b>不叠加</b>：一单最多命中一个活动，多活动并发时取「优惠金额最大者」，避免折上折口径争议；</li>
 *   <li><b>可叠加</b>：活动与优惠券、积分可叠加，计价顺序固定为 活动 → 券 → 积分；</li>
 *   <li><b>成本归属</b>：平台活动由平台承担（结算出平台补贴行，不减店铺应付），
 *       本店活动由店铺承担（结算出店铺减项）；</li>
 *   <li><b>并发</b>：发布/结束均走 {@code @Version} 乐观锁更新，冲突抛 40900。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private static final Map<Integer, String> TYPE_DESC = Map.of(
            Promotion.TYPE_CASH, "满减",
            Promotion.TYPE_DISCOUNT, "折扣",
            Promotion.TYPE_FLASH, "限时购");

    private static final Map<Integer, String> SCOPE_DESC = Map.of(
            Promotion.SCOPE_PLATFORM, "平台活动",
            Promotion.SCOPE_STORE, "本店活动");

    private static final Map<Integer, String> STATUS_DESC = Map.of(
            Promotion.STATUS_DRAFT, "草稿",
            Promotion.STATUS_RUNNING, "进行中",
            Promotion.STATUS_ENDED, "已结束");

    private final PromotionMapper promotionMapper;
    private final ObjectMapper objectMapper;

    // ==================== 管理端 ====================

    @Override
    public IPage<PromotionVO> pagePromotions(PromotionQuery query) {
        PromotionQuery q = normalize(query);
        IPage<PromotionVO> page = promotionMapper.pagePromotions(new Page<>(q.getPage(), q.getSize()), q);
        page.getRecords().forEach(this::fillDerived);
        return page;
    }

    @Override
    public PromotionVO getPromotion(Long id) {
        Promotion p = requirePromotion(id);
        PromotionVO vo = toVO(p);
        fillDerived(vo);
        return vo;
    }

    @Override
    @Transactional
    public Long createPromotion(PromotionSaveRequest req, Long operatorStoreId) {
        validateSave(req);
        Long storeId = resolveStoreId(req, operatorStoreId);

        Promotion p = new Promotion();
        applyReq(p, req, storeId);
        p.setStatus(Promotion.STATUS_DRAFT);
        promotionMapper.insert(p);
        log.info("创建促销活动 id={} title={} type={} scope={} storeId={}",
                p.getId(), p.getTitle(), p.getType(), p.getScope(), storeId);
        return p.getId();
    }

    @Override
    @Transactional
    public void updatePromotion(Long id, PromotionSaveRequest req, Long operatorStoreId) {
        validateSave(req);
        Promotion p = requirePromotion(id);
        if (p.getStatus() != Promotion.STATUS_DRAFT) {
            throw new BizException(ResultCode.CONFLICT, "仅「草稿」状态的活动可编辑，进行中请先结束再新建");
        }
        Long storeId = resolveStoreId(req, operatorStoreId);
        applyReq(p, req, storeId);
        if (promotionMapper.updateById(p) == 0) {
            throw BizException.conflict("活动已被他人修改，请刷新后重试");
        }
        log.info("更新促销活动 id={} title={}", id, p.getTitle());
    }

    @Override
    @Transactional
    public void publishPromotion(Long id) {
        Promotion p = requirePromotion(id);
        if (p.getStatus() == Promotion.STATUS_RUNNING) {
            throw BizException.conflict("该活动已在进行中");
        }
        if (p.getStatus() == Promotion.STATUS_ENDED) {
            throw new BizException("已结束的活动不可重新发布，请新建");
        }
        if (p.getEndTime() != null && !p.getEndTime().isAfter(LocalDateTime.now())) {
            throw new BizException("活动结束时间已过，请先修改时间再发布");
        }
        Promotion up = new Promotion();
        up.setId(id);
        up.setStatus(Promotion.STATUS_RUNNING);
        up.setVersion(p.getVersion());
        if (promotionMapper.updateById(up) == 0) {
            throw BizException.conflict("活动已被他人修改，请刷新后重试");
        }
        log.info("发布促销活动 id={} title={} 窗口={} ~ {}", id, p.getTitle(), p.getStartTime(), p.getEndTime());
    }

    @Override
    @Transactional
    public void stopPromotion(Long id) {
        Promotion p = requirePromotion(id);
        if (p.getStatus() == Promotion.STATUS_ENDED) {
            throw BizException.conflict("该活动已结束");
        }
        Promotion up = new Promotion();
        up.setId(id);
        up.setStatus(Promotion.STATUS_ENDED);
        up.setVersion(p.getVersion());
        if (promotionMapper.updateById(up) == 0) {
            throw BizException.conflict("活动已被他人修改，请刷新后重试");
        }
        log.info("提前结束促销活动 id={} title={}", id, p.getTitle());
    }

    // ==================== C 端 / 计价 ====================

    @Override
    public List<PromotionVO> listActive(Long storeId) {
        List<Promotion> list = (storeId == null || storeId <= 0)
                ? promotionMapper.listActivePlatform()
                : promotionMapper.listActive(storeId);
        return list.stream().map(p -> {
            PromotionVO vo = toVO(p);
            fillDerived(vo);
            return vo;
        }).toList();
    }

    @Override
    public PromotionMatch matchBest(Long storeId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return null;
        }
        List<Promotion> actives = promotionMapper.listActive(storeId);
        Promotion best = null;
        BigDecimal bestDiscount = BigDecimal.ZERO;
        for (Promotion p : actives) {
            BigDecimal d = calcDiscount(p, amount);
            if (d.signum() > 0 && d.compareTo(bestDiscount) > 0) {
                best = p;
                bestDiscount = d;
            }
        }
        if (best == null) {
            return null;
        }
        log.info("活动命中 storeId={} amount={} promotionId={} type={} 优惠={}",
                storeId, amount, best.getId(), best.getType(), bestDiscount);
        return new PromotionMatch(best.getId(), best.getTitle(), best.getScope(), bestDiscount);
    }

    /**
     * 按活动规则计算优惠金额。
     *
     * <p>门槛判定用传入金额（下单侧为商品小计）；结果按订单金额封顶，避免出现负应付。
     */
    @Override
    public BigDecimal calcDiscount(Promotion p, BigDecimal amount) {
        BigDecimal base = amount == null ? BigDecimal.ZERO : amount;
        if (base.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        JsonNode rules = parseRules(p.getRules());
        BigDecimal threshold = readDecimal(rules, "thresholdAmount", BigDecimal.ZERO);
        if (base.compareTo(threshold) < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount;
        if (p.getType() != null && p.getType() == Promotion.TYPE_DISCOUNT) {
            BigDecimal rate = readDecimal(rules, "discountRate", null);
            if (rate == null) {
                throw new BizException("折扣活动缺少折扣率（rules.discountRate）");
            }
            discount = base.multiply(BigDecimal.ONE.subtract(rate)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cap = readDecimal(rules, "maxDiscount", null);
            if (cap != null && cap.signum() > 0) {
                discount = discount.min(cap);
            }
        } else {
            discount = readDecimal(rules, "discountAmount", BigDecimal.ZERO);
        }
        return discount.max(BigDecimal.ZERO).min(base).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public int closeExpired() {
        int n = promotionMapper.closeExpired();
        if (n > 0) {
            log.info("活动到点自动结束 count={}", n);
        }
        return n;
    }

    // ==================== 私有方法 ====================

    private void validateSave(PromotionSaveRequest req) {
        if (req.getEndTime() != null && req.getStartTime() != null
                && !req.getEndTime().isAfter(req.getStartTime())) {
            throw new BizException("结束时间须晚于开始时间");
        }
        Integer type = req.getType();
        if (type == null || (type != Promotion.TYPE_CASH
                && type != Promotion.TYPE_DISCOUNT && type != Promotion.TYPE_FLASH)) {
            throw new BizException("活动类型不合法：1满减 / 2折扣 / 3限时购");
        }
        if (req.getScope() == null
                || (req.getScope() != Promotion.SCOPE_PLATFORM && req.getScope() != Promotion.SCOPE_STORE)) {
            throw new BizException("活动归属不合法：1平台活动 / 2本店活动");
        }
        JsonNode rules = parseRules(req.getRules());
        if (rules == null || !rules.isObject()) {
            throw new BizException("活动规则必须是 JSON 对象");
        }
        BigDecimal threshold = readDecimal(rules, "thresholdAmount", BigDecimal.ZERO);
        if (threshold.signum() < 0) {
            throw new BizException("门槛金额不能为负");
        }
        if (type == Promotion.TYPE_DISCOUNT) {
            BigDecimal rate = readDecimal(rules, "discountRate", null);
            if (rate == null) {
                throw new BizException("折扣活动须提供规则：{\"thresholdAmount\":0,\"discountRate\":0.88,\"maxDiscount\":30.00}");
            }
            if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                throw new BizException("折扣率不合法：须在 (0,1) 区间，如 0.88 表示 88 折");
            }
        } else {
            BigDecimal discount = readDecimal(rules, "discountAmount", null);
            if (discount == null || discount.signum() <= 0) {
                throw new BizException("满减/限时购活动的优惠金额必须大于 0（rules.discountAmount）");
            }
            if (discount.compareTo(threshold) <= 0 && threshold.signum() > 0) {
                // 门槛高于优惠没有意义，但不阻断（可能是刻意设置的长期活动），仅记录
                log.warn("活动优惠金额（{}）未高于门槛（{}），命中价值有限", discount, threshold);
            }
        }
    }

    /**
     * 归属门店解析：店长（operatorStoreId 非空）只能建本店活动且归属本店；
     * 总部可建平台活动（storeId 为空）或指定门店活动。
     */
    private Long resolveStoreId(PromotionSaveRequest req, Long operatorStoreId) {
        if (operatorStoreId != null && operatorStoreId > 0) {
            if (req.getScope() == Promotion.SCOPE_PLATFORM) {
                throw new BizException("门店账号只能创建本店活动");
            }
            return operatorStoreId;
        }
        if (req.getScope() == Promotion.SCOPE_STORE) {
            if (req.getStoreId() == null || req.getStoreId() <= 0) {
                throw new BizException("本店活动必须指定归属门店");
            }
            return req.getStoreId();
        }
        if (req.getStoreId() != null) {
            throw new BizException("平台活动不归属具体门店");
        }
        return null;
    }

    private void applyReq(Promotion p, PromotionSaveRequest req, Long storeId) {
        p.setTitle(req.getTitle());
        p.setType(req.getType());
        p.setScope(req.getScope());
        p.setStoreId(storeId);
        p.setRules(req.getRules());
        p.setStartTime(req.getStartTime());
        p.setEndTime(req.getEndTime());
    }

    private JsonNode parseRules(String rules) {
        if (!StringUtils.hasText(rules)) {
            return null;
        }
        try {
            return objectMapper.readTree(rules);
        } catch (Exception e) {
            throw new BizException("活动规则 JSON 格式不正确");
        }
    }

    private BigDecimal readDecimal(JsonNode rules, String field, BigDecimal defaultValue) {
        if (rules == null || !rules.hasNonNull(field)) {
            return defaultValue;
        }
        try {
            return new BigDecimal(rules.get(field).asText());
        } catch (NumberFormatException e) {
            throw new BizException("活动规则字段格式不正确：" + field);
        }
    }

    private Promotion requirePromotion(Long id) {
        Promotion p = promotionMapper.selectById(id);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "促销活动不存在");
        }
        return p;
    }

    private PromotionVO toVO(Promotion p) {
        PromotionVO vo = new PromotionVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setType(p.getType());
        vo.setScope(p.getScope());
        vo.setStoreId(p.getStoreId());
        vo.setRules(p.getRules());
        vo.setStartTime(p.getStartTime());
        vo.setEndTime(p.getEndTime());
        vo.setStatus(p.getStatus());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }

    /** 补齐派生展示字段：类型/归属/状态文案、规则摘要、当前是否生效 */
    private void fillDerived(PromotionVO vo) {
        vo.setTypeDesc(TYPE_DESC.getOrDefault(vo.getType(), "#" + vo.getType()));
        vo.setScopeDesc(SCOPE_DESC.getOrDefault(vo.getScope(), "#" + vo.getScope()));
        vo.setStatusDesc(STATUS_DESC.getOrDefault(vo.getStatus(), "#" + vo.getStatus()));

        JsonNode rules = null;
        try {
            rules = parseRules(vo.getRules());
        } catch (BizException ignore) {
            // 历史脏数据不阻断列表展示
        }
        BigDecimal threshold = readDecimal(rules, "thresholdAmount", BigDecimal.ZERO);
        vo.setThresholdAmount(threshold);
        if (vo.getType() != null && vo.getType() == Promotion.TYPE_DISCOUNT) {
            BigDecimal rate = readDecimal(rules, "discountRate", null);
            vo.setDiscountRate(rate);
            vo.setRuleDesc(describeDiscount(threshold, rate, readDecimal(rules, "maxDiscount", null)));
        } else {
            BigDecimal amount = readDecimal(rules, "discountAmount", BigDecimal.ZERO);
            vo.setDiscountAmount(amount);
            vo.setRuleDesc(describeCash(threshold, amount));
        }

        LocalDateTime now = LocalDateTime.now();
        boolean inWindow = vo.getStartTime() != null && vo.getEndTime() != null
                && !now.isBefore(vo.getStartTime()) && !now.isAfter(vo.getEndTime());
        vo.setActive(vo.getStatus() != null && vo.getStatus() == Promotion.STATUS_RUNNING && inWindow);
    }

    private String describeCash(BigDecimal threshold, BigDecimal amount) {
        if (threshold == null || threshold.signum() <= 0) {
            return "立减 ¥" + amount;
        }
        return "满 ¥" + threshold.stripTrailingZeros().toPlainString() + " 减 ¥"
                + amount.stripTrailingZeros().toPlainString();
    }

    private String describeDiscount(BigDecimal threshold, BigDecimal rate, BigDecimal cap) {
        if (rate == null) {
            return "折扣规则缺失";
        }
        String fold = rate.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString();
        String head = (threshold == null || threshold.signum() <= 0)
                ? "打 " + fold + " 折"
                : "满 ¥" + threshold.stripTrailingZeros().toPlainString() + " 打 " + fold + " 折";
        if (cap != null && cap.signum() > 0) {
            head += "（最高减 ¥" + cap.stripTrailingZeros().toPlainString() + "）";
        }
        return head;
    }

    private PromotionQuery normalize(PromotionQuery q) {
        PromotionQuery n = q == null ? new PromotionQuery() : q;
        n.setPage(normPage(n.getPage()));
        n.setSize(normSize(n.getSize()));
        return n;
    }

    private long normPage(long p) {
        return p <= 0 ? 1 : p;
    }

    private long normSize(long s) {
        return Math.min(s <= 0 ? 10 : s, 100);
    }
}
