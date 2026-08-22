package com.raglaw.server.web;

import com.raglaw.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminProbeController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of("message", "admin ok"));
    }
}
