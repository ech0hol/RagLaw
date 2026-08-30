package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.rag.dto.RetrievalHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalSubstantiveFilterTest {

    private final RetrievalSubstantiveFilter filter = new RetrievalSubstantiveFilter();

    @Test
    void filtersTableOfContentsHits() {
        RetrievalHit toc = new RetrievalHit(
                "toc-1",
                "doc-1",
                """
                第一编 总则
                第三章 刑罚
                第一节 刑罚的种类
                """,
                "STATUTE",
                "/STATUTE/CRIMINAL",
                "/STATUTE/CRIMINAL/GENERAL",
                0.9
        );
        RetrievalHit article = new RetrievalHit(
                "art-33",
                "doc-1",
                "第三十三条 刑罚分为主刑和附加刑。",
                "STATUTE",
                "/STATUTE/CRIMINAL",
                "/STATUTE/CRIMINAL/GENERAL",
                0.8
        );

        List<RetrievalHit> filtered = filter.filter(List.of(toc, article));

        assertThat(filtered).extracting(RetrievalHit::chunkId).containsExactly("art-33");
    }

    @Test
    void returnsEmptyWhenAllHitsAreTableOfContents() {
        RetrievalHit toc = new RetrievalHit(
                "toc-1",
                "doc-1",
                """
                第一编 总则
                第三章 刑罚
                """,
                "STATUTE",
                "/STATUTE/CRIMINAL",
                "/STATUTE/CRIMINAL/GENERAL",
                0.9
        );

        List<RetrievalHit> filtered = filter.filter(List.of(toc));

        assertThat(filtered).isEmpty();
    }
}
