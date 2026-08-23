package com.raglaw.server.auth;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.common.util.Ids;
import com.raglaw.server.auth.dto.AdminUserDto;
import com.raglaw.server.auth.dto.CreateUserRequest;
import com.raglaw.server.domain.UserEntity;
import com.raglaw.server.domain.UserRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> listUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AdminUserDto::from)
                .toList();
    }

    @Transactional
    public AdminUserDto createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "邮箱已存在");
        }
        UserEntity user = new UserEntity(
                Ids.newId(),
                request.email().trim().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                request.role()
        );
        userRepository.save(user);
        return AdminUserDto.from(user);
    }
}
