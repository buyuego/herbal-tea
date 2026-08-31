package com.herbaltea.module.auth;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.herbaltea.module.auth.entity.AdminUser;
import com.herbaltea.module.auth.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

/**
 * 初始超管密码治理（README 待办，安全收口）
 *
 * <p>V2__init_data.sql 中 admin 的 password_hash 为占位值（{@value PLACEHOLDER_HASH}），
 * 本 Runner 启动时检测并处理：
 * <ul>
 *   <li><b>CLI 显式重置</b>：启动参数 {@code --reset-admin-password}（可追加
 *       {@code --admin-password=xxx} 指定密码，缺省自动生成）→ 重置后打印一次性密码并退出，不启动 Web</li>
 *   <li><b>dev 环境</b>：检测到占位密码 → 自动生成 16 位强随机密码落库，醒目日志提示（仅首次，落库后不再重复）</li>
 *   <li><b>prod 环境</b>：检测到占位密码 → <b>快速失败拒绝启动</b>，提示先执行 CLI 重置（安全默认）</li>
 * </ul>
 *
 * <p>无论何种方式重置，密码哈希均以 BCrypt 存储；明文密码只出现在本次启动日志，请立即登录修改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminPasswordResetRunner implements ApplicationRunner {

    /** 与 V2__init_data.sql 中初始超管占位哈希保持一致的检测锚点（默认值，可经配置覆盖） */
    static final String PLACEHOLDER_HASH = "$2a$10$PLACEHOLDER.RESET.VIA.CLI.BEFORE.FIRST.LOGIN";

    private static final char[] CHARS = (
            "ABCDEFGHJKLMNPQRSTUVWXYZ" + // 去 I/O
            "abcdefghijkmnopqrstuvwxyz" + // 去 l
            "23456789" +                  // 去 0/1
            "!@#$%^&*-_=+").toCharArray();

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${app.admin.initial-username:admin}")
    private String initialUsername;

    @Value("${app.admin.placeholder-hash:" + PLACEHOLDER_HASH + "}")
    private String placeholderHash;

    @Override
    public void run(ApplicationArguments args) {
        boolean cliReset = args.containsOption("reset-admin-password");
        String explicitPassword = args.getOptionValues("admin-password") == null
                ? null : args.getOptionValues("admin-password").get(0);

        AdminUser admin = adminUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, initialUsername));

        if (cliReset) {
            handleCliReset(admin, explicitPassword);
        } else if (admin != null && placeholderHash.equals(admin.getPasswordHash())) {
            handlePlaceholderOnStartup(admin);
        }
    }

    /** CLI 模式：重置密码 → 打印一次性密码 → 退出进程（不启动 Web 服务） */
    private void handleCliReset(AdminUser admin, String explicitPassword) {
        if (admin == null) {
            log.error("CLI 重置失败：初始管理员 '{}' 不存在，请检查 V2__init_data.sql", initialUsername);
            System.exit(1);
            return;
        }
        String raw = StringUtils.hasText(explicitPassword) ? explicitPassword : generatePassword();
        String hash = passwordEncoder.encode(raw);
        adminUserMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .eq(AdminUser::getId, admin.getId())
                .set(AdminUser::getPasswordHash, hash)
                .set(AdminUser::getTokenVersion, 0)); // 重置后历史令牌全部作废（R9）
        log.warn("========================================================");
        log.warn("  初始管理员密码已重置：{}", initialUsername);
        log.warn("  新密码（仅本次显示，请立即登录修改）：{}", raw);
        log.warn("  已重置 token_version，历史 JWT 全部失效");
        log.warn("========================================================");
        System.exit(0);
    }

    /** 启动检测到占位密码：dev 自动重置；prod 快速失败 */
    private void handlePlaceholderOnStartup(AdminUser admin) {
        boolean isProd = environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"));
        if (isProd) {
            throw new IllegalStateException(
                    "生产环境禁止使用占位密码启动。请先执行：java -jar herbal-tea.jar --reset-admin-password");
        }
        String raw = generatePassword();
        String hash = passwordEncoder.encode(raw);
        adminUserMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .eq(AdminUser::getId, admin.getId())
                .set(AdminUser::getPasswordHash, hash)
                .set(AdminUser::getTokenVersion, 0));
        log.warn("==============================================================");
        log.warn("  检测到初始管理员占位密码，已自动重置（开发环境一次性动作）");
        log.warn("  管理员：{}    新密码：{}", initialUsername, raw);
        log.warn("  请立即登录并修改密码；此日志只出现一次");
        log.warn("==============================================================");
    }

    /** SecureRandom 生成 16 位强密码（排除易混淆字符，至少含大小写/数字/符号各 1） */
    static String generatePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digit = "23456789";
        String symbol = "!@#$%^&*-_=+";
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digit.charAt(random.nextInt(digit.length())));
        sb.append(symbol.charAt(random.nextInt(symbol.length())));
        for (int i = 4; i < 16; i++) {
            sb.append(CHARS[random.nextInt(CHARS.length)]);
        }
        // 洗牌避免固定前缀
        for (int i = 15; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char t = sb.charAt(i);
            sb.setCharAt(i, sb.charAt(j));
            sb.setCharAt(j, t);
        }
        return sb.toString();
    }
}
