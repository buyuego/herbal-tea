package com.herbaltea.module.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 通知 Mapper（v32）
 *
 * <p>继承 BaseMapper 提供 CRUD；分页/聚合查询走 {@link com.herbaltea.module.notification.mapper.NotificationMapper.xml}。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 标记单条已读（带权限校验：仅本人通知）
     *
     * @return 受影响行数（0=通知不存在或非本人）
     */
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /**
     * 标记当前用户全部已读
     */
    int markAllRead(@Param("recipientId") Long recipientId);

    /**
     * 删除单条（带权限校验）
     */
    int deleteOwned(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /**
     * 当前用户未读数
     */
    Long countUnread(@Param("recipientId") Long recipientId);

    /**
     * 分页查询（按 recipient 过滤，可选未读/业务类型）
     *
     * @param unreadOnly 仅未读
     * @param bizType    业务类型过滤
     * @param offset     偏移
     * @param size       条数
     */
    java.util.List<com.herbaltea.module.notification.entity.Notification> selectPageByRecipient(
            @Param("recipientId") Long recipientId,
            @Param("unreadOnly") Boolean unreadOnly,
            @Param("bizType") String bizType,
            @Param("offset") Long offset,
            @Param("size") int size);

    /**
     * 最近通知（按接收人列表拉取，用于铃铛摘要）
     */
    java.util.List<com.herbaltea.module.notification.entity.Notification> selectRecentByRecipients(
            @Param("recipientIds") java.util.List<Long> recipientIds,
            @Param("limit") int limit);

    /**
     * 按角色+门店查接收人 id 列表（联表 store_admins + admin_users）
     *
     * <p>用于按角色批量推送：role 4 店长 / role 5 店员 需限定门店。
     * role=NULL 时查全平台绑定（超管/财务/仓管不绑门店，传 storeId=NULL）。
     */
    java.util.List<Long> selectRecipientIdsByRoleAndStore(
            @Param("role") Integer role,
            @Param("storeId") Long storeId);
}