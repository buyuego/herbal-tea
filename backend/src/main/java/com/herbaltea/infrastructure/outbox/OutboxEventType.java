package com.herbaltea.infrastructure.outbox;

/**
 * Outbox 事件类型（设计文档 2.3 事件清单，10 个）
 *
 * <p>订阅关系：各模块通过 {@link EventSubscriber} 声明关注类型，Worker 分发时按订阅匹配。
 */
public enum OutboxEventType {

    /** 订单支付成功：营销积分、结算分账、发货任务创建 */
    order_paid,

    /** 订单发货：订阅消息推送、签收超时计时 */
    order_shipped,

    /** 退款审批通过：支付原路退款、结算冲正 */
    refund_approved,

    /** 30 分钟未支付自动关单 + 库存回滚 */
    order_auto_closed,

    /** 24h 未审批退款自动升级总部兜底 */
    refund_auto_escalated,

    /** 店铺催发货：仓管工作台提醒 + 发货超时预警 */
    order_urged,

    /** 发货 15 天无签收自动视为签收，解锁积分/结算 */
    order_auto_signed,

    /** 3 天自动确认结算，结算单进入平台审核 */
    settlement_confirmed,

    /** 总部修改平台商品，标记本店目录已更新（D14，不自动覆盖本店定价） */
    product_catalog_changed,

    /** 每日积分过期回收 + 到期前 7 天提醒（D8） */
    points_expired,
}
