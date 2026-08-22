package com.raglaw.chat.web;

import com.raglaw.chat.dto.AppendMessageRequest;
import com.raglaw.chat.dto.ConversationDto;
import com.raglaw.chat.dto.CreateConversationRequest;
import com.raglaw.chat.dto.MessageDto;
import com.raglaw.chat.dto.UpdateConversationTitleRequest;
import com.raglaw.chat.service.ConversationService;
import com.raglaw.common.api.ApiResponse;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.auth.CurrentUserHolder;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ApiResponse<List<ConversationDto>> list() {
        String userId = requireUserId();
        if (userId == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        return ApiResponse.ok(conversationService.listByUser(userId));
    }

    @PostMapping
    public ApiResponse<ConversationDto> create(@RequestBody(required = false) CreateConversationRequest request) {
        String userId = requireUserId();
        if (userId == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        String agentCode = request == null ? null : request.agentCode();
        return ApiResponse.ok(conversationService.create(userId, agentCode));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationDto> get(@PathVariable("id") String conversationId) {
        String userId = requireUserId();
        if (userId == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        return conversationService.get(userId, conversationId)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(ErrorCodes.NOT_FOUND, "会话不存在"));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ConversationDto> updateTitle(
            @PathVariable("id") String conversationId,
            @Valid @RequestBody UpdateConversationTitleRequest request
    ) {
        String userId = requireUserId();
        if (userId == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        return conversationService.updateTitle(userId, conversationId, request.title())
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(ErrorCodes.NOT_FOUND, "会话不存在"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String conversationId) {
        String userId = requireUserId();
        if (userId == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        if (!conversationService.delete(userId, conversationId)) {
            return ApiResponse.fail(ErrorCodes.NOT_FOUND, "会话不存在");
        }
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageDto>> listMessages(@PathVariable("id") String conversationId) {
        String userId = requireUserId();
        if (userId == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        return conversationService.listMessages(userId, conversationId)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(ErrorCodes.NOT_FOUND, "会话不存在"));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<MessageDto> appendMessage(
            @PathVariable("id") String conversationId,
            @Valid @RequestBody AppendMessageRequest request
    ) {
        String userId = requireUserId();
        if (userId == null) {
            return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        return conversationService.appendMessage(
                        userId,
                        conversationId,
                        request.role(),
                        request.content(),
                        request.citationsJson()
                )
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(ErrorCodes.NOT_FOUND, "会话不存在"));
    }

    private static String requireUserId() {
        return CurrentUserHolder.get();
    }
}
