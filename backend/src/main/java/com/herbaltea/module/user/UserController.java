package com.herbaltea.module.user;

import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.user.dto.AddressRequest;
import com.herbaltea.module.user.dto.UserLoginVO;
import com.herbaltea.module.user.dto.UserProfileVO;
import com.herbaltea.module.user.dto.WxLoginRequest;
import com.herbaltea.module.user.entity.UserAddress;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口（C 端小程序）
 *
 * <p>wx-login / refresh 在鉴权白名单内（拦截器链自动放行），
 * 其余端点从 Authorization: Bearer 解析 UserContext.userId。
 */
@Tag(name = "用户", description = "C 端微信登录 / 会员资料 / 收货地址")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 刷新请求（与 B 端一致：刷新令牌走请求体，D12） */
    public record RefreshRequest(
            @NotBlank(message = "刷新令牌不能为空") String refreshToken) {
    }

    /** 资料更新请求（空值不更新） */
    public record ProfileRequest(String nickname, String avatarUrl) {
    }

    @Operation(summary = "微信小程序登录", description = "code 换 openid（dev mock）；首次自动注册；返回双令牌（访问 2h + 刷新 30d 轮换）")
    @PostMapping("/wx-login")
    public Result<UserLoginVO> wxLogin(@Valid @RequestBody WxLoginRequest req, HttpServletRequest request) {
        return Result.ok(userService.wxLogin(req, clientIp(request)));
    }

    @Operation(summary = "刷新令牌轮换", description = "旧刷新令牌作废并签发新双令牌（D12）")
    @PostMapping("/refresh")
    public Result<UserLoginVO> refresh(@Valid @RequestBody RefreshRequest body) {
        return Result.ok(userService.refresh(body.refreshToken()));
    }

    @Operation(summary = "登出", description = "token_version +1 使全部已签发 JWT 秒级失效（R9）")
    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout(UserContext.userId());
        return Result.ok();
    }

    @Operation(summary = "会员资料（脱敏）")
    @GetMapping("/profile")
    public Result<UserProfileVO> profile() {
        return Result.ok(userService.getProfile(UserContext.userId()));
    }

    @Operation(summary = "更新资料", description = "昵称/头像，空值不更新")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody ProfileRequest body) {
        userService.updateProfile(UserContext.userId(), body.nickname(), body.avatarUrl());
        return Result.ok();
    }

    @Operation(summary = "收货地址列表", description = "默认地址置顶")
    @GetMapping("/addresses")
    public Result<List<UserAddress>> addresses() {
        return Result.ok(userService.listAddresses(UserContext.userId()));
    }

    @Operation(summary = "新增收货地址", description = "isDefault=1 时自动清空其他默认")
    @PostMapping("/addresses")
    public Result<UserAddress> addAddress(@Valid @RequestBody AddressRequest body) {
        return Result.ok(userService.addAddress(UserContext.userId(), body));
    }

    @Operation(summary = "修改收货地址", description = "仅本人地址可改；isDefault=1 时自动清空其他默认")
    @PutMapping("/addresses/{id}")
    public Result<UserAddress> updateAddress(@PathVariable Long id, @Valid @RequestBody AddressRequest body) {
        return Result.ok(userService.updateAddress(UserContext.userId(), id, body));
    }

    @Operation(summary = "删除收货地址")
    @DeleteMapping("/addresses/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id) {
        userService.deleteAddress(UserContext.userId(), id);
        return Result.ok();
    }

    /** 客户端 IP：优先 X-Forwarded-For（Nginx 透传），否则取 remoteAddr */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
