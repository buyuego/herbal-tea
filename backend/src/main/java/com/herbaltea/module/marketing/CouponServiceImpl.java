package com.herbaltea.module.marketing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.module.marketing.dto.CouponQuery;
import com.herbaltea.module.marketing.dto.CouponSaveRequest;
import com.herbaltea.module.marketing.dto.CouponUseResult;
import com.herbaltea.module.marketing.dto.CouponVO;
import com.herbaltea.module.marketing.dto.UserCouponVO;
import com.herbaltea.module.marketing.entity.Coupon;
import com.herbaltea.module.marketing.entity.UserCoupon;
import com.herbaltea.module.marketing.mapper.CouponMapper;
import com.herbaltea.module.marketing.mapper.UserCouponMapper;
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
 * 优惠券服务实现（v28）
 *
 * <p>并发控制与积分同款手法，全程无分布式锁：
 * <ul>
 *   <li>领券：{@code received_count < total_count} 条件的原子 UPDATE（零超发）</li>
 *   <li>核销：{@code status = 0} 条件的原子 UPDATE（零重复核销）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private static final Map<Integer, String> TYPE_DESC = Map.of(
            Coupon.TYPE_CASH, "满减券",
            Coupon.TYPE_DISCOUNT, "折扣券");

    private static final Map<Integer, String> SCOPE_DESC = Map.of(
            Coupon.SCOPE_PLATFORM, "平台券",
            Coupon.SCOPE_STORE, "本店券");

    private static final Map<Integer, String> STATUS_DESC = Map.of(
            UserCoupon.STATUS_UNUSED, "未使用",
            UserCoupon.STATUS_USED, "已使用",
            UserCoupon.STATUS_EXPIRED, "已过期",
            UserCoupon.STATUS_REFUNDED, "退款退回");

    /** 券状态文案 */
    private static final Map<Integer, String> COUPON_STATUS_DESC = Map.of(
            Coupon.STATUS_DRAFT, "未发布",
            Coupon.STATUS_PUBLISHED, "发放中",
            Coupon.STATUS_STOPPED, "已停止");

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final ObjectMapper objectMapper;

    // ==================== 券模板 ====================

    @Override
    public IPage<CouponVO> pageCoupons(CouponQuery query) {
        CouponQuery q = normalize(query);
        IPage<CouponVO> page = couponMapper.pageCoupons(new Page<>(q.getPage(), q.getSize()), q);
        page.getRecords().forEach(this::fillDesc);
        return page;
    }

    @Override
    public CouponVO getCoupon(Long id) {
        Coupon c = couponMapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "券模板不存在");
        }
        return toVO(c);
    }

    @Override
    @Transactional
    public Long createCoupon(CouponSaveRequest req, Long operatorStoreId) {
        validateSave(req);
        Long storeId = resolveStoreId(req, operatorStoreId);

        Coupon c = new Coupon();
        applyReq(c, req, storeId);
        c.setStatus(Coupon.STATUS_DRAFT);
        c.setReceivedCount(0);
        couponMapper.insert(c);
        log.info("创建券模板 id={} name={} type={} scope={} storeId={}",
                c.getId(), c.getName(), c.getType(), c.getScope(), storeId);
        return c.getId();
    }

    @Override
    @Transactional
    public void updateCoupon(Long id, CouponSaveRequest req, Long operatorStoreId) {
        validateSave(req);
        Coupon c = couponMapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "券模板不存在");
        }
        if (c.getStatus() != Coupon.STATUS_DRAFT) {
            throw new BizException(ResultCode.CONFLICT, "仅「未发布」的券可编辑，已发放券请停止后新建");
        }
        if (c.getReceivedCount() != null && c.getReceivedCount() > 0) {
            throw new BizException(ResultCode.CONFLICT, "已有用户领取，不可再编辑");
        }
        Long storeId = resolveStoreId(req, operatorStoreId);
        applyReq(c, req, storeId);
        if (couponMapper.updateById(c) == 0) {
            throw BizException.conflict("券模板已被他人修改，请刷新后重试");
        }
        log.info("更新券模板 id={} name={}", id, c.getName());
    }

    @Override
    @Transactional
    public void publishCoupon(Long id) {
        Coupon c = requireCoupon(id);
        if (c.getStatus() == Coupon.STATUS_PUBLISHED) {
            throw BizException.conflict("该券已处于发放中");
        }
        if (c.getStatus() == Coupon.STATUS_STOPPED) {
            throw new BizException("已停止的券不可重新发布，请新建");
        }
        Coupon up = new Coupon();
        up.setId(id);
        up.setStatus(Coupon.STATUS_PUBLISHED);
        up.setVersion(c.getVersion());
        if (couponMapper.updateById(up) == 0) {
            throw BizException.conflict("券模板已被他人修改，请刷新后重试");
        }
        log.info("发布券模板 id={} name={}", id, c.getName());
    }

    @Override
    @Transactional
    public void stopCoupon(Long id) {
        Coupon c = requireCoupon(id);
        if (c.getStatus() == Coupon.STATUS_STOPPED) {
            throw BizException.conflict("该券已停止发放");
        }
        Coupon up = new Coupon();
        up.setId(id);
        up.setStatus(Coupon.STATUS_STOPPED);
        up.setVersion(c.getVersion());
        if (couponMapper.updateById(up) == 0) {
            throw BizException.conflict("券模板已被他人修改，请刷新后重试");
        }
        log.info("停止发放券模板 id={} name={}", id, c.getName());
    }

    // ==================== 领券 ====================

    @Override
    @Transactional
    public Long grantCoupon(Long couponId, Long userId) {
        Coupon c = requireCoupon(couponId);
        LocalDateTime now = LocalDateTime.now();

        if (c.getStatus() != Coupon.STATUS_PUBLISHED) {
            throw new BizException("该券未处于发放中，不可领取");
        }
        if (now.isBefore(c.getStartTime())) {
            throw new BizException("该券尚未开始发放");
        }
        if (now.isAfter(c.getEndTime())) {
            throw new BizException("该券已过发放期");
        }
        if (userCouponMapper.countReceived(userId, couponId) >= c.getPerUserLimit()) {
            throw new BizException("已达每人限领数量：" + c.getPerUserLimit());
        }
        // 原子占位：received_count < total_count 才递增（零超发）
        if (couponMapper.incrReceived(couponId) == 0) {
            throw new BizException("券已被领完");
        }

        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStoreId(c.getScope() == Coupon.SCOPE_STORE ? c.getStoreId() : null);
        uc.setStatus(UserCoupon.STATUS_UNUSED);
        uc.setReceivedAt(now);
        uc.setExpireAt(c.getEndTime());
        userCouponMapper.insert(uc);
        log.info("领券 couponId={} userId={} userCouponId={}", couponId, userId, uc.getId());
        return uc.getId();
    }

    @Override
    public IPage<UserCouponVO> pageUserCoupons(Long userId, Integer status, long page, long size) {
        IPage<UserCouponVO> p = userCouponMapper.pageByUser(
                new Page<>(normPage(page), normSize(size)), userId, status);
        p.getRecords().forEach(this::fillUserCouponDesc);
        return p;
    }

    @Override
    public IPage<UserCouponVO> pageCouponGrants(Long couponId, long page, long size) {
        IPage<UserCouponVO> p = userCouponMapper.pageByCoupon(
                new Page<>(normPage(page), normSize(size)), couponId);
        p.getRecords().forEach(this::fillUserCouponDesc);
        return p;
    }

    // ==================== 核销 ====================

    @Override
    @Transactional
    public CouponUseResult useCoupon(Long userCouponId, Long userId, Long storeId, BigDecimal orderAmount) {
        BigDecimal discount = calcDiscount(userCouponId, userId, storeId, orderAmount);
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        Coupon c = couponMapper.selectById(uc.getCouponId());

        // 原子核销：status=0 才置为已使用（零重复核销，order_id 由下单后回填）
        if (userCouponMapper.markUsed(userCouponId, userId, null) == 0) {
            throw BizException.conflict("该券已被使用或已失效，请刷新后重试");
        }

        CouponUseResult r = new CouponUseResult();
        r.setUserCouponId(userCouponId);
        r.setCouponId(c.getId());
        r.setDiscountAmount(discount);
        r.setScope(c.getScope());
        log.info("核销券 userCouponId={} couponId={} userId={} 优惠={} scope={}",
                userCouponId, c.getId(), userId, discount, c.getScope());
        return r;
    }

    @Override
    public void bindOrderId(Long userCouponId, Long orderId) {
        if (userCouponId == null || orderId == null) {
            return;
        }
        UserCoupon up = new UserCoupon();
        up.setId(userCouponId);
        up.setOrderId(orderId);
        userCouponMapper.updateById(up);
    }

    @Override
    @Transactional
    public int refundCoupons(Long orderId) {
        if (orderId == null) {
            return 0;
        }
        List<UserCoupon> used = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getOrderId, orderId)
                .eq(UserCoupon::getStatus, UserCoupon.STATUS_USED));
        int n = 0;
        for (UserCoupon uc : used) {
            if (userCouponMapper.markRefunded(uc.getId()) > 0) {
                n++;
            }
        }
        if (n > 0) {
            log.info("退款退回券 orderId={} count={}", orderId, n);
        }
        return n;
    }

    @Override
    public BigDecimal calcDiscount(Long userCouponId, Long userId, Long storeId, BigDecimal orderAmount) {
        if (userCouponId == null || userId == null || orderAmount == null) {
            return BigDecimal.ZERO;
        }
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) {
            throw new BizException(ResultCode.NOT_FOUND, "持券记录不存在");
        }
        if (!uc.getUserId().equals(userId)) {
            throw new BizException("不可使用他人的优惠券");
        }
        if (uc.getStatus() != UserCoupon.STATUS_UNUSED) {
            throw new BizException("该券不可使用（已使用/已过期/已退回）");
        }
        if (uc.getExpireAt() == null || LocalDateTime.now().isAfter(uc.getExpireAt())) {
            throw new BizException("该券已过期");
        }

        Coupon c = couponMapper.selectById(uc.getCouponId());
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "券模板不存在");
        }
        // 本店券仅限归属门店使用；平台券全店通用
        if (c.getScope() == Coupon.SCOPE_STORE
                && (storeId == null || !storeId.equals(c.getStoreId()))) {
            throw new BizException("该券仅限指定门店使用");
        }
        BigDecimal base = orderAmount == null ? BigDecimal.ZERO : orderAmount;
        BigDecimal threshold = c.getThresholdAmount() == null ? BigDecimal.ZERO : c.getThresholdAmount();
        if (base.compareTo(threshold) < 0) {
            throw new BizException("订单未达使用门槛：需满 ¥" + threshold + "，当前 ¥" + base);
        }

        BigDecimal discount = calcByType(c, base);
        // 封顶：不超过订单金额，避免出现负应付
        return discount.min(base).setScale(2, RoundingMode.HALF_UP);
    }

    /** 按券类型计算优惠（满减直减 / 折扣按比例，可选封顶） */
    private BigDecimal calcByType(Coupon c, BigDecimal base) {
        if (c.getType() == Coupon.TYPE_CASH) {
            return c.getDiscountAmount() == null ? BigDecimal.ZERO : c.getDiscountAmount();
        }
        // 折扣券：rules = {"discountRate":0.85,"maxDiscount":20.00}
        JsonNode rules = parseRules(c.getRules());
        if (rules == null || !rules.has("discountRate")) {
            throw new BizException("折扣券缺少折扣率（rules.discountRate）");
        }
        BigDecimal rate = new BigDecimal(rules.get("discountRate").asText());
        if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            throw new BizException("折扣率不合法：须在 (0,1) 区间，如 0.85 表示 85 折");
        }
        BigDecimal discount = base.multiply(BigDecimal.ONE.subtract(rate))
                .setScale(2, RoundingMode.HALF_UP);
        if (rules.has("maxDiscount")) {
            BigDecimal cap = new BigDecimal(rules.get("maxDiscount").asText());
            discount = discount.min(cap);
        }
        return discount;
    }

    // ==================== 私有方法 ====================

    private void validateSave(CouponSaveRequest req) {
        if (req.getEndTime() != null && req.getStartTime() != null
                && !req.getEndTime().isAfter(req.getStartTime())) {
            throw new BizException("失效时间须晚于生效时间");
        }
        if (req.getType() == null
                || (req.getType() != Coupon.TYPE_CASH && req.getType() != Coupon.TYPE_DISCOUNT)) {
            throw new BizException("券类型不合法：1满减券 / 2折扣券");
        }
        if (req.getScope() == null
                || (req.getScope() != Coupon.SCOPE_PLATFORM && req.getScope() != Coupon.SCOPE_STORE)) {
            throw new BizException("券归属不合法：1平台券 / 2本店券");
        }
        if (req.getTotalCount() == null || req.getTotalCount() <= 0) {
            throw new BizException("发行总量必须大于 0");
        }
        if (req.getPerUserLimit() == null || req.getPerUserLimit() < 1) {
            throw new BizException("每人限领至少为 1");
        }
        if (req.getType() == Coupon.TYPE_CASH) {
            if (req.getDiscountAmount() == null || req.getDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("满减券的优惠金额必须大于 0");
            }
        } else {
            JsonNode rules = parseRules(req.getRules());
            if (rules == null || !rules.has("discountRate")) {
                throw new BizException("折扣券须提供规则：{\"discountRate\":0.85,\"maxDiscount\":20.00}");
            }
        }
    }

    /**
     * 归属门店解析：店长（operatorStoreId 非空）只能建本店券且归属本店；
     * 总部可建平台券（storeId 为空）或指定门店券。
     */
    private Long resolveStoreId(CouponSaveRequest req, Long operatorStoreId) {
        if (operatorStoreId != null && operatorStoreId > 0) {
            if (req.getScope() == Coupon.SCOPE_PLATFORM) {
                throw new BizException("门店账号只能创建本店券");
            }
            return operatorStoreId;
        }
        if (req.getScope() == Coupon.SCOPE_STORE) {
            if (req.getStoreId() == null || req.getStoreId() <= 0) {
                throw new BizException("本店券必须指定归属门店");
            }
            return req.getStoreId();
        }
        if (req.getStoreId() != null) {
            throw new BizException("平台券不归属具体门店");
        }
        return null;
    }

    private void applyReq(Coupon c, CouponSaveRequest req, Long storeId) {
        c.setName(req.getName());
        c.setType(req.getType());
        c.setScope(req.getScope());
        c.setStoreId(storeId);
        c.setThresholdAmount(req.getThresholdAmount() == null ? BigDecimal.ZERO : req.getThresholdAmount());
        c.setDiscountAmount(req.getType() == Coupon.TYPE_CASH ? req.getDiscountAmount() : BigDecimal.ZERO);
        c.setRules(req.getRules());
        c.setTotalCount(req.getTotalCount());
        c.setPerUserLimit(req.getPerUserLimit());
        c.setStartTime(req.getStartTime());
        c.setEndTime(req.getEndTime());
    }

    private JsonNode parseRules(String rules) {
        if (!StringUtils.hasText(rules)) {
            return null;
        }
        try {
            return objectMapper.readTree(rules);
        } catch (Exception e) {
            throw new BizException("券规则 JSON 格式不正确");
        }
    }

    private Coupon requireCoupon(Long id) {
        Coupon c = couponMapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "券模板不存在");
        }
        return c;
    }

    private CouponVO toVO(Coupon c) {
        CouponVO vo = new CouponVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setType(c.getType());
        vo.setScope(c.getScope());
        vo.setStoreId(c.getStoreId());
        vo.setThresholdAmount(c.getThresholdAmount());
        vo.setDiscountAmount(c.getDiscountAmount());
        vo.setRules(c.getRules());
        vo.setTotalCount(c.getTotalCount());
        vo.setReceivedCount(c.getReceivedCount());
        vo.setPerUserLimit(c.getPerUserLimit());
        vo.setStartTime(c.getStartTime());
        vo.setEndTime(c.getEndTime());
        vo.setStatus(c.getStatus());
        vo.setCreatedAt(c.getCreatedAt());
        vo.setUpdatedAt(c.getUpdatedAt());
        fillDesc(vo);
        return vo;
    }

    private void fillDesc(CouponVO vo) {
        vo.setTypeDesc(TYPE_DESC.getOrDefault(vo.getType(), "#" + vo.getType()));
        vo.setScopeDesc(SCOPE_DESC.getOrDefault(vo.getScope(), "#" + vo.getScope()));
        vo.setStatusDesc(COUPON_STATUS_DESC.getOrDefault(vo.getStatus(), "#" + vo.getStatus()));
        int total = vo.getTotalCount() == null ? 0 : vo.getTotalCount();
        int received = vo.getReceivedCount() == null ? 0 : vo.getReceivedCount();
        vo.setRemainCount(Math.max(total - received, 0));
    }

    private void fillUserCouponDesc(UserCouponVO vo) {
        vo.setTypeDesc(TYPE_DESC.getOrDefault(vo.getType(), "#" + vo.getType()));
        vo.setScopeDesc(SCOPE_DESC.getOrDefault(vo.getScope(), "#" + vo.getScope()));
        vo.setStatusDesc(STATUS_DESC.getOrDefault(vo.getStatus(), "#" + vo.getStatus()));
    }

    private CouponQuery normalize(CouponQuery q) {
        CouponQuery n = q == null ? new CouponQuery() : q;
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
