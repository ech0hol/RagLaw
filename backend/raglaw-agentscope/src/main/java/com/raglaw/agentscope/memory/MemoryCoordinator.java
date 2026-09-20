package com.raglaw.agentscope.memory;

import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.port.MemoryCandidateExtractor;
import com.raglaw.memory.service.CaseMemoryCommandService;
import com.raglaw.memory.service.MemoryCandidate;
import com.raglaw.memory.service.MemoryCommandResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Service;

@Service
public class MemoryCoordinator {
    private final MemoryCandidateExtractor extractor;
    private final CaseMemoryCommandService commandService;
    private final Executor extractionExecutor;

    public MemoryCoordinator(MemoryCandidateExtractor extractor,
                             CaseMemoryCommandService commandService,
                             Executor memoryExtractionExecutor) {
        this.extractor = extractor;
        this.commandService = commandService;
        this.extractionExecutor = memoryExtractionExecutor;
    }

    /** Explicit corrections are executed before a workflow snapshot is frozen. */
    public MemoryProcessingResult processBeforeSnapshot(CaseScope scope, String sourceMessageId,
                                                        String messageText, String actor) {
        if (scope == null || sourceMessageId == null || sourceMessageId.isBlank()) {
            return MemoryProcessingResult.skippedResult();
        }
        if (!CorrectionSignalDetector.isExplicitCorrection(messageText)) {
            return MemoryProcessingResult.skippedResult();
        }
        return new MemoryProcessingResult(extractAndApply(scope, sourceMessageId, messageText, actor), true, false);
    }

    /** Ordinary messages are queued only after the assistant response has been persisted. */
    public CompletableFuture<MemoryProcessingResult> queueOrdinary(CaseScope scope, String sourceMessageId,
                                                                   String messageText, String actor) {
        if (scope == null || sourceMessageId == null || sourceMessageId.isBlank()) {
            return CompletableFuture.completedFuture(MemoryProcessingResult.skippedResult());
        }
        return CompletableFuture.supplyAsync(
                () -> new MemoryProcessingResult(extractAndApply(scope, sourceMessageId, messageText, actor),
                        false, false),
                extractionExecutor);
    }

    private List<MemoryCommandResult> extractAndApply(CaseScope scope, String sourceMessageId,
                                                       String messageText, String actor) {
        List<MemoryCandidate> candidates = extractor.extract(scope, sourceMessageId, messageText);
        return candidates.stream().map(candidate -> commandService.apply(scope, candidate, actor)).toList();
    }
}
