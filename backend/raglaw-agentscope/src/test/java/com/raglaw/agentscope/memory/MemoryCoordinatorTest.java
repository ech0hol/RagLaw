package com.raglaw.agentscope.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.port.MemoryCandidateExtractor;
import com.raglaw.memory.service.CaseMemoryCommandService;
import com.raglaw.memory.service.MemoryCandidate;
import com.raglaw.memory.service.MemoryCommandResult;
import com.raglaw.memory.service.MemoryResolution;
import com.raglaw.memory.domain.MemoryAction;
import com.raglaw.memory.domain.MemorySourceType;
import com.raglaw.memory.domain.VerificationStatus;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MemoryCoordinatorTest {
    private final MemoryCandidateExtractor extractor = Mockito.mock(MemoryCandidateExtractor.class);
    private final CaseMemoryCommandService commandService = Mockito.mock(CaseMemoryCommandService.class);
    private final Executor directExecutor = Runnable::run;
    private final MemoryCoordinator coordinator = new MemoryCoordinator(extractor, commandService, directExecutor);
    private final CaseScope scope = new CaseScope("tenant", "user", "case");

    @Test
    void correctionIsProcessedSynchronouslyBeforeSnapshot() {
        MemoryCandidate candidate = candidate();
        when(extractor.extract(scope, "message-1", "刚才说错了，月薪是18000")).thenReturn(List.of(candidate));
        when(commandService.apply(scope, candidate, "user")).thenReturn(result());

        MemoryProcessingResult result = coordinator.processBeforeSnapshot(scope, "message-1", "刚才说错了，月薪是18000", "user");

        assertThat(result.synchronous()).isTrue();
        assertThat(result.results()).hasSize(1);
        verify(commandService).apply(scope, candidate, "user");
    }

    @Test
    void ordinaryMessageIsQueuedAndDoesNotBlockCaller() {
        when(extractor.extract(scope, "message-1", "我在上海工作")).thenReturn(List.of(candidate()));
        when(commandService.apply(Mockito.eq(scope), Mockito.any(), Mockito.eq("user"))).thenReturn(result());

        MemoryProcessingResult result = coordinator.queueOrdinary(scope, "message-1", "我在上海工作", "user").join();

        assertThat(result.synchronous()).isFalse();
        assertThat(result.skipped()).isFalse();
    }

    private static MemoryCandidate candidate() {
        return new MemoryCandidate("EMPLOYEE", "SELF", "MONTHLY_SALARY", "{\"amount\":18000}",
                null, null, MemorySourceType.USER_MESSAGE, "message-1", true, false);
    }

    private static MemoryCommandResult result() {
        return new MemoryCommandResult(new MemoryResolution(MemoryAction.ADD, List.of(),
                VerificationStatus.UNVERIFIED, "test"), "memory-1", 1, true);
    }
}
