package com.herbaltea.module.user.dto;

import com.herbaltea.module.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 会员资料（脱敏返回）
 */
@Data
@AllArgsConstructor
public class UserProfileVO {

    private Long id;

    private String nickname;

    private String avatarUrl;

    /** 手机号（脱敏：138****1234；未绑定为空） */
    private String phone;

    private Integer status;

    public static UserProfileVO from(User u) {
        String masked = u.getPhone();
        if (masked != null && masked.length() == 11) {
            masked = masked.substring(0, 3) + "****" + masked.substring(7);
        }
        return new UserProfileVO(u.getId(), u.getNickname(), u.getAvatarUrl(), masked, u.getStatus());
    }
}
