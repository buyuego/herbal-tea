package com.herbaltea.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置（仅引入 spring-security-crypto，不启用完整 Security 过滤链）
 *
 * <p>BCrypt 强度 10（与 V2__init_data.sql 中初始超管哈希同策略）；
 * 密码哈希仅存储，任何日志/响应/审计均不得输出明文密码。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
