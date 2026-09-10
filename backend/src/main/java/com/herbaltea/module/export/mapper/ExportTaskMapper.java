package com.herbaltea.module.export.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.export.entity.ExportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 导出任务 Mapper（v33）
 *
 * <p>CAS 抢锁：UPDATE ... SET status=10 WHERE status=0 AND id=#{id} LIMIT 1，
 * 返回受影响行数 = 1 才是抢到，避开 Worker 抢同一任务。
 */
@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTask> {

    /** CAS：原子抢占 pending 任务，rows 影响 1 = 成功 */
    int tryAcquire(@Param("id") Long id);

    /** Worker 扫描候选 pending */
    java.util.List<ExportTask> selectPending(@Param("limit") int limit);

    /** 操作人列表（本人隔离 + 状态过滤） */
    java.util.List<ExportTask> selectByOperator(@Param("operatorId") Long operatorId,
                                                 @Param("status") Integer status,
                                                 @Param("offset") long offset,
                                                 @Param("size") int size);

    /** 操作人总数 */
    long countByOperator(@Param("operatorId") Long operatorId, @Param("status") Integer status);

    /** 标记完成（成功/失败统一接口） */
    int markFinished(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("rowCount") Integer rowCount,
                     @Param("fileSize") Long fileSize,
                     @Param("filePath") String filePath,
                     @Param("errorMsg") String errorMsg,
                     @Param("finishedAt") LocalDateTime finishedAt);
}
