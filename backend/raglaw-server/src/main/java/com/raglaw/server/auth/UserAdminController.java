package com.raglaw.server.auth;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.server.auth.dto.AdminUserDto;
import com.raglaw.server.auth.dto.CreateUserRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public ApiResponse<List<AdminUserDto>> list() {
        return ApiResponse.ok(userAdminService.listUsers());
    }

    @PostMapping
    public ApiResponse<AdminUserDto> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userAdminService.createUser(request));
    }
}
