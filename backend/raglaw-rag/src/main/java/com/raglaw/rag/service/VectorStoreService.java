package com.raglaw.rag.service;

import com.raglaw.rag.config.RagProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(name = "postgresJdbcTemplate")
public class VectorStoreService {

    private final JdbcTemplate jdbcTemplate;
    private final RagProperties ragProperties;

    public VectorStoreService(
            @Qualifier("postgresJdbcTemplate") JdbcTemplate jdbcTemplate,
            RagProperties ragProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragProperties = ragProperties;
    }

    public boolean isEnabled() {
        return ragProperties.getPostgres().isEnabled();
    }

    public void upsert(
            String chunkId,
            String documentId,
            float[] embedding,
            String l1Path,
            String l2Path,
            String l3Path,
            String docType
    ) {
        String vectorLiteral = toVectorLiteral(embedding);
        jdbcTemplate.update("""
                INSERT INTO raglaw_embedding (chunk_id, document_id, embedding, l1_path, l2_path, l3_path, doc_type)
                VALUES (?, ?, ?::vector, ?, ?, ?, ?)
                ON CONFLICT (chunk_id) DO UPDATE SET
                    document_id = EXCLUDED.document_id,
                    embedding = EXCLUDED.embedding,
                    l1_path = EXCLUDED.l1_path,
                    l2_path = EXCLUDED.l2_path,
                    l3_path = EXCLUDED.l3_path,
                    doc_type = EXCLUDED.doc_type
                """,
                chunkId, documentId, vectorLiteral, l1Path, l2Path, l3Path, docType);
    }

    public void deleteByDocumentId(String documentId) {
        jdbcTemplate.update("DELETE FROM raglaw_embedding WHERE document_id = ?", documentId);
    }

    public List<VectorHit> search(float[] queryVector, List<String> scopes, int limit) {
        String vectorLiteral = toVectorLiteral(queryVector);
        if (scopes == null || scopes.isEmpty()) {
            return jdbcTemplate.query("""
                    SELECT chunk_id, document_id, l1_path, l2_path, l3_path,
                           1 - (embedding <=> ?::vector) AS score
                    FROM raglaw_embedding
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                    """,
                    (rs, rowNum) -> new VectorHit(
                            rs.getString("chunk_id"),
                            rs.getString("document_id"),
                            rs.getString("l1_path"),
                            rs.getString("l2_path"),
                            rs.getString("l3_path"),
                            rs.getDouble("score")
                    ),
                    vectorLiteral, vectorLiteral, limit);
        }
        String[] scopeArray = scopes.toArray(String[]::new);
        return jdbcTemplate.query("""
                SELECT chunk_id, document_id, l1_path, l2_path, l3_path,
                       1 - (embedding <=> ?::vector) AS score
                FROM raglaw_embedding
                WHERE l3_path = ANY(?) OR l2_path = ANY(?)
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> new VectorHit(
                        rs.getString("chunk_id"),
                        rs.getString("document_id"),
                        rs.getString("l1_path"),
                        rs.getString("l2_path"),
                        rs.getString("l3_path"),
                        rs.getDouble("score")
                ),
                vectorLiteral, scopeArray, scopeArray, vectorLiteral, limit);
    }

    private static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public record VectorHit(
            String chunkId,
            String documentId,
            String l1Path,
            String l2Path,
            String l3Path,
            double score
    ) {
    }
}
