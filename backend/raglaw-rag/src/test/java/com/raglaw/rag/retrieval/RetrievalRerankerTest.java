package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.rag.dto.RetrievalHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalRerankerTest {

  private final RetrievalReranker reranker = new RetrievalReranker();

  @Test
  void prefersStatuteChunksForStatuteAgent() {
    RetrievalHit statute = new RetrievalHit(
        "s1", "d1", "未及时足额支付劳动报酬", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR", 2.0
    );
    RetrievalHit caseHit = new RetrievalHit(
        "c1", "d2", "张某周末加班案例", "/CASE", "/CASE/CIVIL", "/CASE/CIVIL/LABOR", 2.5
    );

    List<RetrievalHit> ranked = reranker.rerank(List.of(caseHit, statute), "STATUTE_CIVIL");

    assertThat(ranked.get(0).chunkId()).isEqualTo("s1");
  }
}
