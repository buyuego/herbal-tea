package com.herbaltea.module.settlement.task;

import com.herbaltea.infrastructure.scheduler.ScheduledTask;
import com.herbaltea.infrastructure.scheduler.TaskRunner;
import com.herbaltea.module.settlement.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 结算单自动确认任务（第 11 章：生成后 72h 未确认视为无异议，自动进入平台审核）
 *
 * <p>实现要点（与 OrderCloseTask 同款手法）：
 * <ul>
 *   <li>cron 每 1 分钟扫描 status=10 AND auto_confirm_at &lt;= NOW() 的结算单</li>
 *   <li>分布式锁（Redis SETNX）防多实例并发扫描</li>
 *   <li>逐单走 {@link SettlementService#autoConfirm}：CAS 10→20 + confirm_status=1 自动确认，
 *       单笔失败不影响其他结算单（下轮重扫，幂等）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ScheduledTask(task = "settlement_auto_confirm")
public class SettlementAutoConfirmTask {

    private static final int BATCH_LIMIT = 100;

    private final TaskRunner taskRunner;
    private final SettlementService settlementService;

    @Scheduled(cron = "0 * * * * ?")
    public void run() {
        if (!taskRunner.acquireLock("settlement_auto_confirm")) {
            return;
        }
        try {
            List<Long> ids = settlementService.listAutoConfirmable(BATCH_LIMIT);
            if (ids.isEmpty()) {
                return;
            }
            log.info("[任务] 扫描到 {} 张到期未确认结算单，自动确认", ids.size());
            for (Long id : ids) {
                try {
                    settlementService.autoConfirm(id);
                } catch (Exception e) {
                    log.error("[任务] 结算单自动确认失败 settlementId={}（跳过，下轮重试）", id, e);
                }
            }
        } finally {
            taskRunner.recordSuccess("settlement_auto_confirm");
        }
    }
}
