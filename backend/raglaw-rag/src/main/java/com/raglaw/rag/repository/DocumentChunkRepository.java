package com.raglaw.rag.repository;

import com.raglaw.rag.domain.DocumentChunkEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, String> {

    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndexAsc(String documentId);

    @Modifying
    @Transactional
    void deleteByDocumentId(String documentId);

    @Query(value = """
            SELECT c.id, c.document_id, c.content, c.l1_path, c.l2_path, c.l3_path,
                   MATCH(c.content) AGAINST(:query IN NATURAL LANGUAGE MODE) AS score
            FROM raglaw_document_chunk c
            INNER JOIN raglaw_document d ON d.id = c.document_id
            WHERE d.status = 'INDEXED'
              AND MATCH(c.content) AGAINST(:query IN NATURAL LANGUAGE MODE)
              AND (:scopeCount = 0 OR c.l3_path IN (:scopes) OR c.l2_path IN (:scopes))
            ORDER BY score DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> searchFullText(
            @Param("query") String query,
            @Param("scopes") List<String> scopes,
            @Param("scopeCount") int scopeCount,
            @Param("limit") int limit
    );
}
