package com.raglaw.agentscope.agui;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class AguiRunServiceUserMessageTest {

    @Test
    void masksSqlExceptionMessage() throws Exception {
        String message = invokeToUserMessage(new SQLException("INSERT INTO raglaw_shadow_route_log ..."));
        assertThat(message).isEqualTo("服务暂时异常，请稍后重试");
    }

    @Test
    void masksDataAccessExceptionMessage() throws Exception {
        String message = invokeToUserMessage(new DataAccessException("could not execute statement") {});
        assertThat(message).isEqualTo("服务暂时异常，请稍后重试");
    }

    @Test
    void masksRawSqlInMessage() throws Exception {
        String message = invokeToUserMessage(new RuntimeException("INSERT INTO raglaw_shadow_route_log values ..."));
        assertThat(message).isEqualTo("服务暂时异常，请稍后重试");
    }

    @Test
    void keepsIllegalArgumentMessage() throws Exception {
        String message = invokeToUserMessage(new IllegalArgumentException("消息不能为空"));
        assertThat(message).isEqualTo("消息不能为空");
    }

    private static String invokeToUserMessage(Throwable error) throws Exception {
        Method method = AguiRunService.class.getDeclaredMethod("toUserMessage", Throwable.class);
        method.setAccessible(true);
        return (String) method.invoke(null, error);
    }
}
