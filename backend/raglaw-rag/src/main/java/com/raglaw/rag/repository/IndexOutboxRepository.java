package com.raglaw.rag.repository;

import com.raglaw.rag.domain.IndexOutboxEntity;
import com.raglaw.rag.domain.IndexOutboxStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IndexOutboxRepository extends JpaRepository<IndexOutboxEntity, String> {

    List<IndexOutboxEntity> findByDocumentIdAndStatusOrderByCreatedAtAsc(String documentId, IndexOutboxStatus status);

    List<IndexOutboxEntity> findByStatusOrderByCreatedAtAsc(IndexOutboxStatus status, Pageable pageable);

    @Query("""
            SELECT o FROM IndexOutboxEntity o
            WHERE o.status = :status
              AND o.updatedAt <= :retryBefore
            ORDER BY o.createdAt ASC
            """)
    List<IndexOutboxEntity> findRetryableFailed(
            @Param("status") IndexOutboxStatus status,
            @Param("retryBefore") Instant retryBefore,
            Pageable pageable
    );
}
