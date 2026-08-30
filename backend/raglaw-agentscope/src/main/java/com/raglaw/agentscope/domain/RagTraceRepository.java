package com.raglaw.agentscope.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RagTraceRepository extends JpaRepository<RagTraceEntity, String> {

    List<RagTraceEntity> findTop50ByOrderByCreatedAtDesc();

    Page<RagTraceEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            SELECT t FROM RagTraceEntity t
            WHERE (:agentCode IS NULL OR :agentCode = '' OR t.agentCode = :agentCode)
              AND (:q IS NULL OR :q = '' OR LOWER(t.queryText) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY t.createdAt DESC
            """)
    Page<RagTraceEntity> search(
            @Param("agentCode") String agentCode,
            @Param("q") String q,
            Pageable pageable
    );
}
