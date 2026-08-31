package com.herbaltea.infrastructure.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * event_outbox 表 Mapper（SQL 与 V1__schema.sql 列名/状态码严格对齐）
 *
 * <p>Worker 领取使用 {@code FOR UPDATE SKIP LOCKED}（MySQL 8）避免多线程重复领取；
 * 单实例下仍保留，作为防御纵深（16.12 幂等兜底）。
 */
@Mapper
public interface OutboxMapper extends BaseMapper<OutboxEvent> {

    /**
     * 领取待投递事件：status=0（待投递，含退避重试中）且到期。
     * 终态（status=2 超限人工）不会被选中。
     */
    @Select("SELECT * FROM event_outbox " +
            "WHERE status = 0 AND next_retry_at <= #{now} " +
            "ORDER BY id ASC LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<OutboxEvent> pollPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** CAS 领取：仅 status=0 可抢占，防并发重复投递（成功返回 1） */
    @Update("UPDATE event_outbox SET status = 1, delivered_at = NOW() " +
            "WHERE event_id = #{eventId} AND status = 0")
    int claim(@Param("eventId") String eventId);

    /** 积压监控：待投递且已到期的数量（16.2 监控项） */
    @Select("SELECT COUNT(*) FROM event_outbox WHERE status = 0 AND next_retry_at <= NOW()")
    long countBacklog();
}
