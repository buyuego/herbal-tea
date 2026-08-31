package com.herbaltea.module.marketing.task;

import com.herbaltea.infrastructure.scheduler.ScheduledTask;
import com.herbaltea.infrastructure.scheduler.TaskRunner;
import com.herbaltea.module.marketing.MarketingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 积分过期回收任务（设计 D8 / 12.1：每日凌晨 3 点）
 *
 * <p>实现要点（与 SettlementAutoConfirmTask 同款手法）：
 * <ul>
 *   <li>分布式锁（Redis SETNX）防多实例并发</li>
 *   <li>{@link MarketingService#expirePoints()} 内部幂等：回收前先写 change_type=4 流水
 *       （biz_key=expire:{batchNo}），重复扫描会被 NOT EXISTS 排除</li>
 *   <li>单批次上限 500，防止长事务；剩余下轮继续</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ScheduledTask(task = "points_expire")
public class PointsExpireTask {

    private final TaskRunner taskRunner;
    private final MarketingService marketingService;

    /** 每日 03:00 执行 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void run() {
        if (!taskRunner.acquireLock("points_expire")) {
            return;
        }
        try {
            int handled = marketingService.expirePoints();
            if (handled > 0) {
                log.info("[任务] 积分过期清零完成，批次 {} 个", handled);
            }
        } catch (Exception e) {
            log.error("[任务] 积分过期回收失败（下轮重试）", e);
        } finally {
            taskRunner.recordSuccess("points_expire");
        }
    }
}
