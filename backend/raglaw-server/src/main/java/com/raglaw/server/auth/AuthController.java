package com.raglaw.server.auth;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.server.auth.dto.LoginRequest;
import com.raglaw.server.auth.dto.LoginResponse;
import com.raglaw.server.auth.dto.UserDto;
import com.raglaw.server.domain.UserEntity;
import com.raglaw.server.domain.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "raglaw_refresh";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public AuthController(
            UserRepository userRepository,
            JwtService jwtService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        UserEntity user = userRepository.findByEmail(request.email())
                .filter(UserEntity::isEnabled)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "邮箱或密码错误");
        }
        String access = jwtService.createAccessToken(user);
        String refresh = jwtService.createRefreshToken(user);
        setRefreshCookie(response, refresh);
        return ApiResponse.ok(new LoginResponse(access, "Bearer", toDto(user)));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @org.springframework.web.bind.annotation.CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "缺少 refresh token");
        }
        try {
            var claims = jwtService.parse(refreshToken);
            UserEntity user = userRepository.findById(claims.getSubject())
                    .filter(UserEntity::isEnabled)
                    .orElse(null);
            if (user == null) {
                return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "用户不存在");
            }
            String access = jwtService.createAccessToken(user);
            String refresh = jwtService.createRefreshToken(user);
            setRefreshCookie(response, refresh);
            return ApiResponse.ok(new LoginResponse(access, "Bearer", toDto(user)));
        } catch (Exception ex) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "refresh token 无效");
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        clearRefreshCookie(response);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me() {
        AuthUser current = UserContext.get();
        if (current == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        return ApiResponse.ok(new UserDto(current.id(), current.email(), current.displayName(), current.role()));
    }

    private static UserDto toDto(UserEntity user) {
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }

    private static void setRefreshCookie(HttpServletResponse response, String refresh) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, refresh)
                .httpOnly(true)
                .path("/api/v1/auth")
                .maxAge(7 * 24 * 60 * 60L)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private static void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
