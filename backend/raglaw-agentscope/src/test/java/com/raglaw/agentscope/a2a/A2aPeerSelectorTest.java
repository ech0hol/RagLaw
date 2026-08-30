package com.raglaw.agentscope.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2aPeerSelectorTest {

    @Mock
    private A2aRoutingLlm routingLlm;

    private A2aPeerSelector selector;

    @BeforeEach
    void setUp() {
        selector = new A2aPeerSelector(routingLlm);
    }

    @Test
    void routesLaborQuestionToStatute() {
        PeerSelection selection = selector.select(
                A2aPeerSelector.MVP_A2A_PEERS,
                "公司拖欠工资如何维权"
        );

        assertEquals("STATUTE", selection.peerCode());
        assertFalse(selection.usedLlm());
    }

    @Test
    void routesExcessivePenaltyToStatute() {
        PeerSelection selection = selector.select(
                A2aPeerSelector.MVP_A2A_PEERS,
                "违约金过高能否调减"
        );

        assertEquals("STATUTE", selection.peerCode());
        assertFalse(selection.usedLlm());
    }

    @Test
    void routesContractQuestionToContract() {
        PeerSelection selection = selector.select(
                A2aPeerSelector.MVP_A2A_PEERS,
                "租赁合同解除条件"
        );

        assertEquals("CONTRACT", selection.peerCode());
        assertFalse(selection.usedLlm());
    }

    @Test
    void routesCaseQuestionToCase() {
        PeerSelection selection = selector.select(
                A2aPeerSelector.MVP_A2A_PEERS,
                "法院判决要点有哪些"
        );

        assertEquals("CASE", selection.peerCode());
        assertFalse(selection.usedLlm());
    }

    @Test
    void marksChemicalQuestionAsLowConfidence() {
        PeerSelection selection = selector.select(
                A2aPeerSelector.MVP_A2A_PEERS,
                "我想买化学品是违法的吗"
        );

        assertEquals("STATUTE", selection.peerCode());
        assertTrue(selection.lowConfidence());
        assertEquals("out_of_scope:STATUTE", selection.reason());
    }
}
