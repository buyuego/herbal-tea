package com.herbaltea.module.order.task;

import com.herbaltea.infrastructure.scheduler.ScheduledTask;
import com.herbaltea.infrastructure.scheduler.TaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 未支付订单自动关单任务（16.12：30 分钟关单）
 *
 * <p>示例任务实现，展示统一任务模式：
 * <ul>
 *   <li>cron 表达式：每 1 分钟扫一次未支付超时订单</li>
 *   <li>幂等：关单 biz_key = order_id（进程崩溃重启后重扫不重复执行）</li>
 *   <li>关单成功：同库本地事务回滚库存 + 发布 order_auto_closed 事件（D10 消除）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ScheduledTask(task = "close_timeout_orders")
public class OrderCloseTask {

    private final TaskRunner taskRunner;

    @Scheduled(cron = "0 * * * * ?")
    public void run() {
        if (!taskRunner.acquireLock("close_timeout_orders")) {
            return;
        }
        try {
            log.info("[任务] 开始扫描超时未支付订单（30 分钟）");
            // TODO: 查询 orders WHERE status='10' AND create_time < NOW() - INTERVAL 30 MINUTE
            //       逐单执行: 乐观锁 CAS 10→70 → 库存回滚 → outboxPublisher.publish(order_auto_closed)
            //       幂等键: "close:{order_id}"（idempotency_keys 表 24h 窗口 + 业务唯一索引兜底）
        } finally {
            taskRunner.recordSuccess("close_timeout_orders");
        }
    }
}
