package com.raglaw.rag.retrieval.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.repository.DocumentChunkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChunkHierarchyResolverTest {

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    private ChunkHierarchyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ChunkHierarchyResolver(documentChunkRepository);
    }

    @Test
    void microMatch_usesMicroTextForDisplayNotChapterHeading() {
        DocumentChunkEntity parent = chunk("parent-1", null, ChunkLevel.PARENT, "", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR");
        DocumentChunkEntity child = chunk("child-1", "parent-1", ChunkLevel.CHILD, "第九章 法律责任", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR");
        DocumentChunkEntity micro = chunk(
                "micro-1",
                "child-1",
                ChunkLevel.MICRO,
                "第九十九条 违反本法规定，构成犯罪的，依法追究刑事责任。",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR"
        );

        when(documentChunkRepository.findById("micro-1")).thenReturn(Optional.of(micro));
        when(documentChunkRepository.findById("child-1")).thenReturn(Optional.of(child));
        when(documentChunkRepository.findById("parent-1")).thenReturn(Optional.of(parent));

        ChunkHierarchyResolver.ResolvedChunk resolved = resolver.resolve("micro-1");

        assertThat(resolved.displayContent()).contains("第九十九条");
        assertThat(resolved.displayContent()).isNotEqualTo("第九章 法律责任");
        assertThat(resolved.llmContent()).contains("第九章 法律责任");
        assertThat(resolved.llmContent()).contains("第九十九条");
    }

  @Test
  void shortChapterHeading_fallsBackToChildAndMicroCombined() {
        DocumentChunkEntity parent = chunk("parent-1", null, ChunkLevel.PARENT, "", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR");
        DocumentChunkEntity child = chunk("child-1", "parent-1", ChunkLevel.CHILD, "第九章 法律责任", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR");
        DocumentChunkEntity micro = chunk(
                "micro-1",
                "child-1",
                ChunkLevel.MICRO,
                "第一百条 本法自公布之日起施行。",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR"
        );

        when(documentChunkRepository.findById("micro-1")).thenReturn(Optional.of(micro));
        when(documentChunkRepository.findById("child-1")).thenReturn(Optional.of(child));
        when(documentChunkRepository.findById("parent-1")).thenReturn(Optional.of(parent));

        ChunkHierarchyResolver.ResolvedChunk resolved = resolver.resolve("micro-1");

        assertThat(resolved.displayContent()).isEqualTo("第一百条 本法自公布之日起施行。");
    }

    @Test
    void headingMicro_fallsBackToSubstantiveSiblingMicro() {
        DocumentChunkEntity parent = chunk("parent-1", null, ChunkLevel.PARENT, "", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR");
        DocumentChunkEntity child = chunk("child-1", "parent-1", ChunkLevel.CHILD, "第九章 法律责任", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR");
        DocumentChunkEntity headingMicro = chunk(
                "micro-heading",
                "child-1",
                ChunkLevel.MICRO,
                "第九章 法律责任",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR"
        );
        DocumentChunkEntity substantiveMicro = chunk(
                "micro-body",
                "child-1",
                ChunkLevel.MICRO,
                "第九十九条 违反本法规定，构成犯罪的，依法追究刑事责任。",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR"
        );

        when(documentChunkRepository.findById("micro-heading")).thenReturn(Optional.of(headingMicro));
        when(documentChunkRepository.findById("child-1")).thenReturn(Optional.of(child));
        when(documentChunkRepository.findById("parent-1")).thenReturn(Optional.of(parent));
        when(documentChunkRepository.findByParentIdOrderByChunkIndexAsc("child-1"))
                .thenReturn(List.of(headingMicro, substantiveMicro));

        ChunkHierarchyResolver.ResolvedChunk resolved = resolver.resolve("micro-heading");

        assertThat(resolved.displayContent()).contains("第九十九条");
        assertThat(resolved.displayContent()).isNotEqualTo("第九章 法律责任");
    }

    @Test
    void substantiveArticleMicro_isNotTreatedAsHeading() {
        DocumentChunkEntity micro = chunk(
                "micro-article",
                "child-1",
                ChunkLevel.MICRO,
                "第十七条 办理购用证明或备案。",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR"
        );
        DocumentChunkEntity child = chunk("child-1", "parent-1", ChunkLevel.CHILD, "第一章 总则", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR");
        DocumentChunkEntity parent = chunk("parent-1", null, ChunkLevel.PARENT, "", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR");

        when(documentChunkRepository.findById("micro-article")).thenReturn(Optional.of(micro));
        when(documentChunkRepository.findById("child-1")).thenReturn(Optional.of(child));
        when(documentChunkRepository.findById("parent-1")).thenReturn(Optional.of(parent));

        ChunkHierarchyResolver.ResolvedChunk resolved = resolver.resolve("micro-article");

        assertThat(resolved.displayContent()).contains("购用证明");
    }

    @Test
    void headingMicro_fallsBackToParentChapterMicro() {
        DocumentChunkEntity parent = chunk("parent-1", null, ChunkLevel.PARENT, "", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR");
        DocumentChunkEntity childHeading = chunk("child-heading", "parent-1", ChunkLevel.CHILD, "第九章 法律责任", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR");
        DocumentChunkEntity childArticle = chunk("child-article", "parent-1", ChunkLevel.CHILD, "第九十九条 违反本法规定，构成犯罪的，依法追究刑事责任。", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR");
        DocumentChunkEntity headingMicro = chunk(
                "micro-heading",
                "child-heading",
                ChunkLevel.MICRO,
                "第九章 法律责任",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR"
        );
        DocumentChunkEntity articleMicro = chunk(
                "micro-article",
                "child-article",
                ChunkLevel.MICRO,
                "第九十九条 违反本法规定，构成犯罪的，依法追究刑事责任。",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR"
        );

        when(documentChunkRepository.findById("micro-heading")).thenReturn(Optional.of(headingMicro));
        when(documentChunkRepository.findById("child-heading")).thenReturn(Optional.of(childHeading));
        when(documentChunkRepository.findById("parent-1")).thenReturn(Optional.of(parent));
        when(documentChunkRepository.findByParentIdOrderByChunkIndexAsc("child-heading")).thenReturn(List.of(headingMicro));
        when(documentChunkRepository.findByParentIdOrderByChunkIndexAsc("parent-1"))
                .thenReturn(List.of(childHeading, childArticle));
        when(documentChunkRepository.findByParentIdOrderByChunkIndexAsc("child-article"))
                .thenReturn(List.of(articleMicro));

        ChunkHierarchyResolver.ResolvedChunk resolved = resolver.resolve("micro-heading");

        assertThat(resolved.displayContent()).contains("第九十九条");
        assertThat(resolved.displayContent()).isNotEqualTo("第九章 法律责任");
    }

    @Test
    void chapterParentWithChildArticle_usesChildBodyNotChapterTitle() {
        DocumentChunkEntity chapterParent = chunk(
                "parent-ch1",
                null,
                ChunkLevel.PARENT,
                "第一章 总则",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR"
        );
        DocumentChunkEntity articleChild = chunk(
                "child-article",
                "parent-ch1",
                ChunkLevel.CHILD,
                "第七条 对危险化学品的生产、储存、使用、经营、运输实施安全监督管理。",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR"
        );

        when(documentChunkRepository.findById("parent-ch1")).thenReturn(Optional.of(chapterParent));
        when(documentChunkRepository.findByParentIdOrderByChunkIndexAsc("parent-ch1"))
                .thenReturn(List.of(articleChild));

        ChunkHierarchyResolver.ResolvedChunk resolved = resolver.resolve("parent-ch1");

        assertThat(resolved.displayContent()).contains("第七条");
        assertThat(resolved.displayContent()).isNotEqualTo("第一章 总则");
    }

    @Test
    void orphanChapterParent_returnsEmptyDisplayContent() {
        DocumentChunkEntity orphanParent = chunk(
                "orphan-ch1",
                null,
                ChunkLevel.PARENT,
                "第一章 总则",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR"
        );

        when(documentChunkRepository.findById("orphan-ch1")).thenReturn(Optional.of(orphanParent));
        when(documentChunkRepository.findByParentIdOrderByChunkIndexAsc("orphan-ch1")).thenReturn(List.of());

        ChunkHierarchyResolver.ResolvedChunk resolved = resolver.resolve("orphan-ch1");

        assertThat(resolved.displayContent()).isBlank();
    }

    private static DocumentChunkEntity chunk(
            String id,
            String parentId,
            ChunkLevel level,
            String content,
            String l1,
            String l2,
            String l3
    ) {
        return new DocumentChunkEntity(id, "doc-1", parentId, level, 0, content, l1, l2, l3, null);
    }
}
