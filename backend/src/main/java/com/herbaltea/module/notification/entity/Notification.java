package com.herbaltea.module.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseCreatedOnlyEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内通知实体（v32）
 *
 * <ul>
 *   <li>无 updated_at 列 → 继承 {@link BaseCreatedOnlyEntity}</li>
 *   <li>无乐观锁（无并发更新场景，仅 read_at 单字段更新）</li>
 *   <li>recipient_role/store_id 冗余，便于按角色/门店聚合查询</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notifications")
public class Notification extends BaseCreatedOnlyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收者管理员id（users.id） */
    private Long recipientId;

    /** 接收者角色id（1超管/2财务/3仓管/4店长/5店员/6加盟店主） */
    private Integer recipientRole;

    /** 业务门店id（用于数据隔离；NULL=超管/财务全平台事件） */
    private Long storeId;

    /** 业务类型：order/refund/settlement/inventory/member */
    private String bizType;

    /** 业务id（订单号/退款单号/结算单号等） */
    private String bizId;

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 点击跳转路径（前端路由） */
    private String link;

    /** 是否已读：0未读/1已读 */
    private Integer isRead;

    /** 已读时间 */
    private java.time.LocalDateTime readAt;
}