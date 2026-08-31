package com.herbaltea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 养生茶小程序后端入口
 *
 * <p>架构：模块化单体（设计文档 v10.0 / ADR-A1）
 * <ul>
 *   <li>单进程部署 8 个业务模块（user/store/product/order/payment/marketing/settlement/auth）</li>
 *   <li>进程内拦截器链（JWT 鉴权 / Data Scope / 限流 / 日志 / 幂等），替代原独立网关</li>
 *   <li>事务性 Outbox（event_outbox 表 + 进程内 Worker 5s 轮询），替代 MQ</li>
 * </ul>
 * <p>Mapper 注册策略：统一使用 {@code @Mapper} 注解（MyBatis-Plus starter 自动发现），
 * 不配置 @MapperScan —— 该注解不支持通配符包名，且会覆盖 @Mapper 自动发现。
 */
@SpringBootApplication
@EnableScheduling
public class HerbalTeaApplication {

    public static void main(String[] args) {
        SpringApplication.run(HerbalTeaApplication.class, args);
    }
}
