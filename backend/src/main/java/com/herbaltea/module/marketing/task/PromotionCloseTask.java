package com.herbaltea.module.marketing.task;

import com.herbaltea.infrastructure.scheduler.ScheduledTask;
import com.herbaltea.infrastructure.scheduler.TaskRunner;
import com.herbaltea.module.marketing.PromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 促销活动到点自动结束任务（v30）
 *
 * <p>与 {@code SettlementAutoConfirmTask} 同款手法：
 * <ul>
 *   <li>Redis SETNX 分布式锁防多实例并发</li>
 *   <li>SQL 幂等：{@code status = 1 AND end_time < NOW()} 才置为已结束，重复扫描不再命中</li>
 *   <li>仅影响状态位，不改动任何金额，失败可安全重试</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ScheduledTask(task = "promotion_close")
public class PromotionCloseTask {

    private final TaskRunner taskRunner;
    private final PromotionService promotionService;

    /** 每 5 分钟扫描一次（活动窗口以分钟为粒度即可） */
    @Scheduled(cron = "0 */5 * * * ?")
    public void run() {
        if (!taskRunner.acquireLock("promotion_close")) {
            return;
        }
        try {
            int n = promotionService.closeExpired();
            if (n > 0) {
                log.info("[任务] 促销活动到点结束完成，活动 {} 个", n);
            }
        } catch (Exception e) {
            log.error("[任务] 促销活动结束扫描失败（下轮重试）", e);
        } finally {
            taskRunner.recordSuccess("promotion_close");
        }
    }
}
