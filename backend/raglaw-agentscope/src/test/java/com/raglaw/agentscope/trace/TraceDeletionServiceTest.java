package com.raglaw.agentscope.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raglaw.agentscope.domain.RagTraceEntity;
import com.raglaw.agentscope.domain.RagTraceRepository;
import com.raglaw.agentscope.domain.ShadowRouteLogRepository;
import com.raglaw.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TraceDeletionServiceTest {

    @Mock
    private RagTraceRepository traceRepository;
    @Mock
    private ShadowRouteLogRepository shadowRouteLogRepository;

    private TraceDeletionService service;

    @BeforeEach
    void setUp() {
        service = new TraceDeletionService(traceRepository, shadowRouteLogRepository);
    }

    @Test
    void deleteRemovesShadowLogsAndTrace() {
        when(traceRepository.existsById("trace-1")).thenReturn(true);

        service.delete("trace-1");

        verify(shadowRouteLogRepository).deleteByTraceId("trace-1");
        verify(traceRepository).deleteById("trace-1");
    }

    @Test
    void deleteThrowsWhenTraceMissing() {
        when(traceRepository.existsById("missing")).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.delete("missing"));
    }

    @Test
    void deleteBatchRemovesOnlyExistingTraces() {
        RagTraceEntity trace1 = new RagTraceEntity(
                "trace-1", "conv-1", "msg-1", "user-1", "问题一", "GENERAL"
        );
        when(traceRepository.findAllById(List.of("trace-1", "missing"))).thenReturn(List.of(trace1));

        int deleted = service.deleteBatch(List.of("trace-1", "missing"));

        assertEquals(1, deleted);
        verify(shadowRouteLogRepository).deleteByTraceIdIn(List.of("trace-1"));
        verify(traceRepository).deleteAll(List.of(trace1));
    }

    @Test
    void deleteBatchRejectsEmptyList() {
        assertThrows(BusinessException.class, () -> service.deleteBatch(List.of()));
    }

    @Test
    void deleteBatchRejectsTooManyIds() {
        List<String> ids = java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> "trace-" + i)
                .toList();

        assertThrows(BusinessException.class, () -> service.deleteBatch(ids));
    }

    @Test
    void deleteBatchReturnsZeroWhenNoneExist() {
        when(traceRepository.findAllById(List.of("missing-1"))).thenReturn(List.of());

        int deleted = service.deleteBatch(List.of("missing-1"));

        assertEquals(0, deleted);
    }
}
