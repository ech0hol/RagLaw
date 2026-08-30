package com.raglaw.rag.repository;

import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    List<DocumentEntity> findByStatusOrderByCreatedAtDesc(DocStatus status);

    List<DocumentEntity> findByDocTypeAndStatus(String docType, DocStatus status);

    List<DocumentEntity> findByDocTypeAndUploaderIdOrderByCreatedAtDesc(String docType, String uploaderId);

    Optional<DocumentEntity> findByIdAndDocTypeAndUploaderId(String id, String docType, String uploaderId);

    List<DocumentEntity> findByDocType(String docType);

    long countByDocTypeAndStatus(String docType, DocStatus status);

    long countByStatusAndDocTypeNotAndIndexVersion(DocStatus status, String docType, long indexVersion);

    List<DocumentEntity> findTop50ByOrderByCreatedAtDesc();

    List<DocumentEntity> findByTitle(String title);

    @Query(value = """
            SELECT d.* FROM raglaw_document d
            INNER JOIN raglaw_category c ON c.id = d.category_id
            WHERE (:docType IS NULL OR d.doc_type = :docType)
              AND (:excludeContract = false OR d.doc_type <> 'CONTRACT')
              AND (
                :categoryPath IS NULL
                OR (
                  :exactMatch = true AND c.path = :categoryPath
                )
                OR (
                  :exactMatch = false AND c.path LIKE CONCAT(:categoryPath, '%')
                )
              )
            ORDER BY d.created_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DocumentEntity> findAdminList(
            @Param("categoryPath") String categoryPath,
            @Param("exactMatch") boolean exactMatch,
            @Param("docType") String docType,
            @Param("excludeContract") boolean excludeContract,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query(value = """
            SELECT COUNT(d.id) FROM raglaw_document d
            INNER JOIN raglaw_category c ON c.id = d.category_id
            WHERE (:docType IS NULL OR d.doc_type = :docType)
              AND (:excludeContract = false OR d.doc_type <> 'CONTRACT')
              AND (
                :categoryPath IS NULL
                OR (
                  :exactMatch = true AND c.path = :categoryPath
                )
                OR (
                  :exactMatch = false AND c.path LIKE CONCAT(:categoryPath, '%')
                )
              )
            """, nativeQuery = true)
    long countAdminList(
            @Param("categoryPath") String categoryPath,
            @Param("exactMatch") boolean exactMatch,
            @Param("docType") String docType,
            @Param("excludeContract") boolean excludeContract
    );

    @Query(value = """
            SELECT c.id, c.path, COUNT(d.id) AS cnt
            FROM raglaw_category c
            LEFT JOIN raglaw_document d ON d.category_id = c.id AND d.doc_type <> 'CONTRACT'
            GROUP BY c.id, c.path
            """, nativeQuery = true)
    List<Object[]> countDocumentsByCategory();

    @Query(value = """
            SELECT d.id, c.path AS category_path,
                   MATCH(d.full_text) AGAINST(:query IN NATURAL LANGUAGE MODE) AS score
            FROM raglaw_document d
            INNER JOIN raglaw_category c ON c.id = d.category_id
            WHERE d.status = 'INDEXED'
              AND d.full_text IS NOT NULL
              AND MATCH(d.full_text) AGAINST(:query IN NATURAL LANGUAGE MODE)
              AND (:scopeCount = 0 OR c.path IN (:scopePaths))
              AND (:lockedDocCount = 0 OR d.id IN (:lockedDocumentIds))
            ORDER BY score DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> searchFullTextDocuments(
            @Param("query") String query,
            @Param("scopePaths") List<String> scopePaths,
            @Param("scopeCount") int scopeCount,
            @Param("lockedDocumentIds") List<String> lockedDocumentIds,
            @Param("lockedDocCount") int lockedDocCount,
            @Param("limit") int limit
    );
}
