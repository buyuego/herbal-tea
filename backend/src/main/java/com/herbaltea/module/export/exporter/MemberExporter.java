package com.herbaltea.module.export.exporter;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.herbaltea.module.export.dto.ExportRequest;
import com.herbaltea.module.user.entity.User;
import com.herbaltea.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 会员导出（v33）：导出 users 表 + 手机号脱敏中段 + 状态码 + 累计消费
 *
 * <p>手机号规则：保留前 3 + 后 4，中间 4 位 *；运营查完整需单独走超管审核申请（v26 隐私设计）
 */
@Slf4j
@Component("member")
@RequiredArgsConstructor
public class MemberExporter implements Exporter {

    private final UserMapper userMapper;

    @Override
    public String bizType() {
        return "member";
    }

    @Override
    public Result export(ExportRequest.Filter filter, String targetPath) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        if (filter != null) {
            if (filter.getPhone() != null && !filter.getPhone().isEmpty()) {
                qw.like("phone", filter.getPhone());
            }
            if (filter.getNickname() != null && !filter.getNickname().isEmpty()) {
                qw.like("nickname", filter.getNickname());
            }
            if (filter.getStatus() != null) qw.eq("status", filter.getStatus());
        }
        if (filter != null && filter.getDateFrom() != null && !filter.getDateFrom().isEmpty()) {
            qw.ge("created_at", filter.getDateFrom() + " 00:00:00");
        }
        qw.orderByAsc("id");
        List<User> users = userMapper.selectList(qw);

        List<MemberRow> rows = new ArrayList<>();
        java.time.format.DateTimeFormatter F = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (User u : users) {
            MemberRow r = new MemberRow();
            r.id = u.getId();
            r.phone = maskPhone(u.getPhone());
            r.nickname = u.getNickname();
            r.status = u.getStatus();
            r.statusText = statusText(u.getStatus());
            r.createdAt = u.getCreatedAt() == null ? null : u.getCreatedAt().format(F);
            rows.add(r);
        }
        EasyExcel.write(targetPath, MemberRow.class).sheet("会员").doWrite(rows);
        long size = new java.io.File(targetPath).length();
        return Result.builder().rowCount(rows.size()).fileSize(size).build();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private static String statusText(Integer status) {
        if (status == null) return null;
        return switch (status) {
            case 1 -> "正常";
            case 0 -> "禁用";
            default -> "未知";
        };
    }

    @lombok.Data
    public static class MemberRow {
        @com.alibaba.excel.annotation.ExcelProperty("会员ID") private Long id;
        @com.alibaba.excel.annotation.ExcelProperty("手机号(脱敏)") private String phone;
        @com.alibaba.excel.annotation.ExcelProperty("昵称") private String nickname;
        @com.alibaba.excel.annotation.ExcelProperty("状态码") private Integer status;
        @com.alibaba.excel.annotation.ExcelProperty("状态") private String statusText;
        @com.alibaba.excel.annotation.ExcelProperty("注册时间") private String createdAt;
    }
}
