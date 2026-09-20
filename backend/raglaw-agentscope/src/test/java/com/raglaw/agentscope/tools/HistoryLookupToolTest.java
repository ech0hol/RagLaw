package com.raglaw.agentscope.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.raglaw.chat.dto.MessageDto;
import com.raglaw.chat.service.ConversationService;
import com.raglaw.memory.casefile.CaseScope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HistoryLookupToolTest {
    @Test
    void onlyReturnsMessagesAuthorizedByCaseScope() {
        ConversationService conversations = Mockito.mock(ConversationService.class);
        when(conversations.findMessageInCase("user", "case", "source-1"))
                .thenReturn(Optional.of(new MessageDto("source-1", "conversation", "user", "合同约定付款期限为30日", null, Instant.now())));
        when(conversations.findMessageInCase("user", "case", "source-2")).thenReturn(Optional.empty());

        String text = new HistoryLookupTool(conversations).lookup(
                new CaseScope("tenant", "user", "case"), List.of("source-1", "source-2"), 5);

        assertThat(text).contains("source-1", "付款期限").doesNotContain("source-2");
        Mockito.verify(conversations).findMessageInCase("user", "case", "source-2");
    }

    @Test
    void boundsLongExcerptsAndResultCount() {
        ConversationService conversations = Mockito.mock(ConversationService.class);
        String longText = "x".repeat(2_500);
        when(conversations.findMessageInCase("user", "case", "source-1"))
                .thenReturn(Optional.of(new MessageDto("source-1", "conversation", "user", longText, null, Instant.now())));

        String text = new HistoryLookupTool(conversations).lookup(
                new CaseScope("tenant", "user", "case"), List.of("source-1"), 1);

        assertThat(text.length()).isLessThan(2_200);
        assertThat(text).endsWith("…");
    }
}
