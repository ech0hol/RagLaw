package com.raglaw.memory.casefile;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.auth.CurrentUserHolder;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {
    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping
    public ApiResponse<List<CaseEntity>> list() {
        String userId = CurrentUserHolder.get();
        if (userId == null) return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "未登录");
        return ApiResponse.ok(caseService.list("default", userId));
    }

    @PostMapping
    public ApiResponse<CaseEntity> create(@RequestBody(required = false) CreateCaseRequest request) {
        String userId = CurrentUserHolder.get();
        if (userId == null) return ApiResponse.fail(ErrorCodes.UNAUTHORIZED, "未登录");
        return ApiResponse.ok(caseService.create("default", userId, request == null ? null : request.title()));
    }

    public record CreateCaseRequest(String title) {
    }
}
