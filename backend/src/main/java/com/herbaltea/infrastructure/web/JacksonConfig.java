package com.herbaltea.infrastructure.web;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * Jackson 时间格式兼容（v28）
 *
 * <p>背景：前端 Element Plus 的日期时间选择器默认输出 {@code yyyy-MM-dd HH:mm:ss}（空格分隔），
 * 而 Jackson 默认只接受 ISO 格式 {@code yyyy-MM-ddTHH:mm:ss}。此前这类请求会抛
 * HttpMessageNotReadableException，被兜底成 50001「系统繁忙」，既误导排查又掩盖了参数问题。
 *
 * <p>这里让反序列化**同时接受**两种分隔（{@code 'T'} 或空格）；序列化仍输出 ISO，
 * 前端 {@code replace('T',' ')} 的处理保持不变。
 */
@Configuration
public class JacksonConfig {

    /** 同时接受 "2026-09-30T10:00:00" 与 "2026-09-30 10:00:00" */
    private static final DateTimeFormatter INBOUND =
            DateTimeFormatter.ofPattern("yyyy-MM-dd['T'][ ]HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        return builder -> builder.deserializers(new LocalDateTimeDeserializer(INBOUND));
    }
}
