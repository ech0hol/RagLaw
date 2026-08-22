package com.raglaw.agentscope.agui;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public final class AguiSseWriter {

    private AguiSseWriter() {
    }

    public static void send(SseEmitter emitter, String event, Map<String, Object> data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
    }
}
