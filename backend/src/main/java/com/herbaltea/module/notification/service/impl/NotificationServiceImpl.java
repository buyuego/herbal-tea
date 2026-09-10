package com.herbaltea.module.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.PageResult;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.module.auth.entity.AdminUser;
import com.herbaltea.module.auth.mapper.AdminUserMapper;
import com.herbaltea.module.notification.dto.BroadcastRequest;
import com.herbaltea.module.notification.dto.NotificationQuery;
import com.herbaltea.module.notification.dto.NotificationSummary;
import com.herbaltea.module.notification.dto.NotificationVO;
import com.herbaltea.module.notification.entity.Notification;
import com.herbaltea.module.notification.mapper.NotificationMapper;
import com.herbaltea.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内通知服务实现（v32）
 *
 * <p>推送策略：
 * <ul>
 *   <li>单推：直接 INSERT 一行</li>
 *   <li>按角色批量：role 1/2/3/6 直查 admin_users；role 4/5 走 store_admins JOIN（按门店过滤）</li>
 *   <li>批量推送一次 INSERT 多行（避免 N 次 IO）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final AdminUserMapper adminUserMapper;

    @Override
    @Transactional
    public void push(Long recipientId, Integer recipientRole, Long storeId,
                     String bizType, String bizId, String title, String content, String link) {
        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setRecipientRole(recipientRole);
        n.setStoreId(storeId);
        n.setBizType(bizType);
        n.setBizId(bizId);
        n.setTitle(title);
        n.setContent(content);
        n.setLink(link);
        n.setIsRead(0);
        notificationMapper.insert(n);
    }

    @Override
    @Transactional
    public int pushByRole(Integer recipientRole, Long storeId,
                          String bizType, String bizId, String title, String content, String link) {
        List<Long> recipientIds = resolveRecipientIds(recipientRole, storeId);
        if (recipientIds.isEmpty()) {
            log.info("推送跳过：role={} storeId={} 无接收人", recipientRole, storeId);
            return 0;
        }
        List<Notification> batch = new ArrayList<>(recipientIds.size());
        for (Long rid : recipientIds) {
            Notification n = new Notification();
            n.setRecipientId(rid);
            n.setRecipientRole(recipientRole);
            n.setStoreId(storeId);
            n.setBizType(bizType);
            n.setBizId(bizId);
            n.setTitle(title);
            n.setContent(content);
            n.setLink(link);
            n.setIsRead(0);
            batch.add(n);
        }
        // 批量插入
        for (Notification n : batch) {
            notificationMapper.insert(n);
        }
        log.info("批量通知推送：role={} storeId={} count={}", recipientRole, storeId, batch.size());
        return batch.size();
    }

    /**
     * 解析接收人 id 列表
     *
     * <ul>
     *   <li>role 4/5 店长/店员：必须带 storeId（走 store_admins JOIN）</li>
     *   <li>其他角色：storeId 可空（NULL=全平台）</li>
     *   <li>role=0（全员）：admin_users WHERE status=1 全员</li>
     * </ul>
     */
    private List<Long> resolveRecipientIds(Integer role, Long storeId) {
        if (role == null || role == 0) {
            // 全员：仅状态正常的 B 端管理员
            List<AdminUser> all = adminUserMapper.selectList(
                    new QueryWrapper<AdminUser>().eq("status", AdminUser.STATUS_ENABLED));
            return all.stream().map(AdminUser::getId).collect(Collectors.toList());
        }
        if (role == 4 || role == 5) {
            // 店长/店员：必须带 storeId
            if (storeId == null) {
                log.warn("店长/店员推送缺少 storeId，role={}", role);
                return List.of();
            }
            // 走 NotificationMapper 跨表联查（基础设施层允许）
            return notificationMapper.selectRecipientIdsByRoleAndStore(role, storeId);
        }
        // 其他角色（1超管/2财务/3仓管/6加盟店主）：直查 admin_users
        List<AdminUser> users = adminUserMapper.selectList(
                new QueryWrapper<AdminUser>()
                        .eq("role_id", role)
                        .eq("status", AdminUser.STATUS_ENABLED));
        return users.stream().map(AdminUser::getId).collect(Collectors.toList());
    }

    @Override
    public NotificationSummary summary(Long recipientId) {
        Long unread = notificationMapper.countUnread(recipientId);
        List<Notification> recents = notificationMapper.selectRecentByRecipients(
                List.of(recipientId), 10);
        List<NotificationVO> vos = recents.stream().map(this::toVO).collect(Collectors.toList());
        return new NotificationSummary(unread == null ? 0L : unread, vos);
    }

    @Override
    public PageResult<NotificationVO> listPage(Long recipientId, NotificationQuery query) {
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query.getSize() == null || query.getSize() < 1 ? 20 : Math.min(query.getSize(), 100);
        long offset = (long) (page - 1) * size;
        List<Notification> rows = notificationMapper.selectPageByRecipient(
                recipientId,
                Boolean.TRUE.equals(query.getUnreadOnly()),
                query.getBizType(),
                offset, size);
        Long totalAll = notificationMapper.selectCount(new QueryWrapper<Notification>()
                .eq("recipient_id", recipientId)
                .eq(Boolean.TRUE.equals(query.getUnreadOnly()), "is_read", 0)
                .eq(query.getBizType() != null && !query.getBizType().isEmpty(), "biz_type", query.getBizType()));
        List<NotificationVO> vos = rows.stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(totalAll == null ? 0L : totalAll, page, size, vos);
    }

    @Override
    @Transactional
    public void markRead(Long id, Long recipientId) {
        int r = notificationMapper.markRead(id, recipientId);
        if (r == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "通知不存在或非本人");
        }
    }

    @Override
    @Transactional
    public int markAllRead(Long recipientId) {
        return notificationMapper.markAllRead(recipientId);
    }

    @Override
    @Transactional
    public void delete(Long id, Long recipientId) {
        int r = notificationMapper.deleteOwned(id, recipientId);
        if (r == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "通知不存在或非本人");
        }
    }

    @Override
    @Transactional
    public int broadcast(Long operatorId, BroadcastRequest req) {
        // operatorId 仅审计用；权限校验由 Controller 层 @PreAuthorize 完成
        return pushByRole(req.getTargetRole() == null ? 0 : req.getTargetRole(),
                req.getTargetStoreId(),
                req.getBizType() == null ? "announcement" : req.getBizType(),
                "broadcast:" + System.currentTimeMillis(),
                req.getTitle(),
                req.getContent(),
                req.getLink());
    }

    @Override
    public NotificationVO toVO(Notification n) {
        if (n == null) return null;
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setBizType(n.getBizType());
        vo.setBizId(n.getBizId());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setLink(n.getLink());
        vo.setIsRead(n.getIsRead());
        vo.setReadAt(n.getReadAt());
        vo.setCreatedAt(n.getCreatedAt());
        vo.setStoreId(n.getStoreId());
        return vo;
    }
}