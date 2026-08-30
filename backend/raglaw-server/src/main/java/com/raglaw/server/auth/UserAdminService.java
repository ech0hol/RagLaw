package com.raglaw.server.auth;

import com.raglaw.chat.domain.ConversationRepository;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.common.util.Ids;
import com.raglaw.server.auth.dto.AdminUserDto;
import com.raglaw.server.auth.dto.CreateUserRequest;
import com.raglaw.server.auth.dto.UpdateUserRequest;
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
    private final ConversationRepository conversationRepository;

    public UserAdminService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ConversationRepository conversationRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.conversationRepository = conversationRepository;
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

    @Transactional
    public AdminUserDto updateUser(String userId, UpdateUserRequest request, String currentUserId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "用户不存在"));
        if (!request.enabled() && userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCodes.VALIDATION, "不能禁用当前登录账号");
        }
        if (!request.role().equals(user.getRole()) && userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCodes.VALIDATION, "不能修改当前登录账号的角色");
        }
        if (wouldRemoveLastEnabledAdmin(user, request)) {
            throw new BusinessException(ErrorCodes.VALIDATION, "系统至少保留一个启用的管理员");
        }
        user.setDisplayName(request.displayName().trim());
        user.setEnabled(request.enabled());
        user.setRole(request.role());
        user.touchUpdatedAt();
        userRepository.save(user);
        return AdminUserDto.from(user);
    }

    @Transactional
    public void deleteUser(String userId, String currentUserId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "用户不存在"));
        if (userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCodes.VALIDATION, "不能删除当前登录账号");
        }
        if ("ADMIN".equals(user.getRole()) && user.isEnabled()
                && userRepository.countByRoleAndEnabled("ADMIN", true) <= 1) {
            throw new BusinessException(ErrorCodes.VALIDATION, "系统至少保留一个启用的管理员");
        }
        conversationRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    private boolean wouldRemoveLastEnabledAdmin(UserEntity user, UpdateUserRequest request) {
        if (!"ADMIN".equals(user.getRole()) || !user.isEnabled()) {
            return false;
        }
        boolean demoting = !"ADMIN".equals(request.role());
        boolean disabling = !request.enabled();
        if (!demoting && !disabling) {
            return false;
        }
        return userRepository.countByRoleAndEnabled("ADMIN", true) <= 1;
    }
}
