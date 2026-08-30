package com.raglaw.agentscope.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.a2a.A2aPeerSelector;
import com.raglaw.agentscope.a2a.A2aRoutingLlm;
import com.raglaw.agentscope.domain.ShadowRouteLogRepository;
import com.raglaw.rag.retrieval.funnel.DocumentCandidate;
import com.raglaw.rag.retrieval.funnel.FunnelResult;
import com.raglaw.rag.retrieval.funnel.KnowledgeFunnelRouter;
import com.raglaw.rag.service.KnowledgeScopeResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShadowRouteObserverTest {

    @Mock
    private KnowledgeFunnelRouter knowledgeFunnelRouter;

    @Mock
    private KnowledgeScopeResolver knowledgeScopeResolver;

    @Mock
    private A2aPeerSelector peerSelector;

    @Mock
    private A2aRoutingLlm routingLlm;

    @Mock
    private ShadowRouteLogRepository shadowRouteLogRepository;

    private ShadowRouteObserver observer;

    @BeforeEach
    void setUp() {
        observer = new ShadowRouteObserver(
                knowledgeFunnelRouter,
                knowledgeScopeResolver,
                peerSelector,
                routingLlm,
                shadowRouteLogRepository,
                new ObjectMapper()
        );
    }

    @Test
    void observeDocumentFromFunnelDoesNotThrowWhenSaveFails() {
        doThrow(new RuntimeException("SQL syntax error")).when(shadowRouteLogRepository).save(any());

        FunnelResult funnel = new FunnelResult(
                List.of("/STATUTE"),
                List.of(),
                List.of(),
                0.8,
                0.7,
                "NONE",
                "",
                false,
                List.of(new DocumentCandidate("doc-1", "中华人民共和国商标法_20251227", "/STATUTE", 0.9))
        );

        observer.observeDocumentFromFunnel("trace-1", null, funnel);

        assertThat(funnel.documentRanking()).hasSize(1);
    }
}
