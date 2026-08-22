package com.raglaw.agentscope.agui;

import com.raglaw.agentscope.agui.dto.AguiRunRequest;
import com.raglaw.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/agui")
public class AguiController {

    private final AguiRunService aguiRunService;

    public AguiController(AguiRunService aguiRunService) {
        this.aguiRunService = aguiRunService;
    }

    @PostMapping("/run")
    public SseEmitter run(@RequestBody AguiRunRequest request) {
        return aguiRunService.run(request);
    }

    @PostMapping("/run/stop")
    public ApiResponse<Map<String, Object>> stop(@RequestParam String taskId) {
        aguiRunService.stop(taskId);
        return ApiResponse.ok(Map.of("taskId", taskId, "stopped", true));
    }
}
