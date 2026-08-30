package com.raglaw.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.DocumentRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentExcerptServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentFullTextService documentFullTextService;

    private DocumentExcerptService service;

    @BeforeEach
    void setUp() {
        service = new DocumentExcerptService(
                documentRepository,
                documentFullTextService,
                new RagProperties()
        );
    }

    @Test
    void returnsExcerptAroundRequestedArticles() {
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(
                new DocumentEntity("doc-1", "cat", "刑法", "STATUTE", "user", "key.md")
        ));
        when(documentFullTextService.buildFullText("doc-1")).thenReturn("""
                第一编 总则
                第三章 刑罚
                第三十二条 刑罚分为主刑和附加刑。
                第三十三条 主刑包括管制、拘役、有期徒刑、无期徒刑和死刑。
                """);

        String excerpt = service.getExcerpt("doc-1", "第32条,第33条").excerpt();

        assertThat(excerpt).contains("第三十二条");
        assertThat(excerpt).contains("第三十三条");
    }
}
