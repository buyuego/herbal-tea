package com.herbaltea.module.notification.service;

import com.herbaltea.common.result.PageResult;
import com.herbaltea.module.notification.dto.BroadcastRequest;
import com.herbaltea.module.notification.dto.NotificationQuery;
import com.herbaltea.module.notification.dto.NotificationSummary;
import com.herbaltea.module.notification.dto.NotificationVO;
import com.herbaltea.module.notification.entity.Notification;

import java.util.List;

/**
 * 站内通知服务（v32）
 */
public interface NotificationService {

    /**
     * 单条推送（一般由订阅者调用）
     */
    void push(Long recipientId, Integer recipientRole, Long storeId,
              String bizType, String bizId, String title, String content, String link);

    /**
     * 按角色批量推送
     */
    int pushByRole(Integer recipientRole, Long storeId,
                   String bizType, String bizId, String title, String content, String link);

    /**
     * 铃铛摘要：未读数 + 最近 N 条
     */
    NotificationSummary summary(Long recipientId);

    /**
     * 分页列表（当前用户视角）
     */
    PageResult<NotificationVO> listPage(Long recipientId, NotificationQuery query);

    /**
     * 标记单条已读（仅本人）
     */
    void markRead(Long id, Long recipientId);

    /**
     * 标记全部已读
     */
    int markAllRead(Long recipientId);

    /**
     * 删除单条（仅本人）
     */
    void delete(Long id, Long recipientId);

    /**
     * 管理员广播（仅超管）
     */
    int broadcast(Long operatorId, BroadcastRequest req);

    /**
     * VO 转换
     */
    NotificationVO toVO(Notification n);
}