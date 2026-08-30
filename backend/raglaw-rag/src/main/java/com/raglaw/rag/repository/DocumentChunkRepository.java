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

    List<DocumentChunkEntity> findByParentIdOrderByChunkIndexAsc(String parentId);

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
              AND (
                c.chunk_level = 'MICRO'
                OR (
                  c.chunk_level = 'CHILD'
                  AND NOT EXISTS (
                    SELECT 1 FROM raglaw_document_chunk micro
                    WHERE micro.document_id = c.document_id AND micro.chunk_level = 'MICRO'
                  )
                )
                OR (
                  c.chunk_level IS NULL
                  AND (
                    c.parent_id IS NOT NULL
                    OR NOT EXISTS (
                      SELECT 1 FROM raglaw_document_chunk child
                      WHERE child.document_id = c.document_id AND child.parent_id IS NOT NULL
                    )
                  )
                )
              )
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

    @Query(value = """
            SELECT c.id, c.document_id, c.content, c.l1_path, c.l2_path, c.l3_path,
                   MATCH(c.content) AGAINST(:query IN NATURAL LANGUAGE MODE) AS score
            FROM raglaw_document_chunk c
            INNER JOIN raglaw_document d ON d.id = c.document_id
            WHERE d.status = 'INDEXED'
              AND c.document_id = :documentId
              AND MATCH(c.content) AGAINST(:query IN NATURAL LANGUAGE MODE)
              AND (
                c.chunk_level = 'MICRO'
                OR (
                  c.chunk_level = 'CHILD'
                  AND NOT EXISTS (
                    SELECT 1 FROM raglaw_document_chunk micro
                    WHERE micro.document_id = c.document_id AND micro.chunk_level = 'MICRO'
                  )
                )
                OR (
                  c.chunk_level IS NULL
                  AND (
                    c.parent_id IS NOT NULL
                    OR NOT EXISTS (
                      SELECT 1 FROM raglaw_document_chunk child
                      WHERE child.document_id = c.document_id AND child.parent_id IS NOT NULL
                    )
                  )
                )
              )
            ORDER BY score DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> searchFullTextForDocument(
            @Param("query") String query,
            @Param("documentId") String documentId,
            @Param("limit") int limit
    );
}
