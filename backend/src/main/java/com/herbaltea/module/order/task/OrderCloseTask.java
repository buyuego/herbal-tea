package com.herbaltea.module.order.task;

import com.herbaltea.infrastructure.scheduler.ScheduledTask;
import com.herbaltea.infrastructure.scheduler.TaskRunner;
import com.herbaltea.module.order.OrderService;
import com.herbaltea.module.order.entity.Order;
import com.herbaltea.module.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 未支付订单自动关单任务（16.12：下单 30 分钟关单）
 *
 * <p>实现要点：
 * <ul>
 *   <li>cron 每 1 分钟扫描 status=10 AND expire_at &lt; NOW() 的订单</li>
 *   <li>分布式锁（Redis SETNX）防多实例并发扫描</li>
 *   <li>逐单走 {@link OrderService#closeExpired}：乐观锁 CAS 10→70（并发/重复扫描幂等）+
 *       库存回滚 + order_auto_closed 事件（D10 消除），单笔失败不影响其他订单</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ScheduledTask(task = "close_timeout_orders")
public class OrderCloseTask {

    private final TaskRunner taskRunner;
    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @Scheduled(cron = "0 * * * * ?")
    public void run() {
        if (!taskRunner.acquireLock("close_timeout_orders")) {
            return;
        }
        try {
            List<Order> expired = orderMapper.selectExpiredPending();
            if (expired.isEmpty()) {
                return;
            }
            log.info("[任务] 扫描到 {} 笔超时未支付订单待关单", expired.size());
            for (Order order : expired) {
                try {
                    orderService.closeExpired(order.getId());
                } catch (Exception e) {
                    log.error("[任务] 关单失败 orderId={}（跳过，下轮重试）", order.getId(), e);
                }
            }
        } finally {
            taskRunner.recordSuccess("close_timeout_orders");
        }
    }
}
