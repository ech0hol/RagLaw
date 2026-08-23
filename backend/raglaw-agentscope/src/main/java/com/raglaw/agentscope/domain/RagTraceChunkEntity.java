package com.raglaw.agentscope.domain;

import com.raglaw.common.jpa.ColumnLengths;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "raglaw_rag_trace_chunk")
public class RagTraceChunkEntity {

    @Id
    @Column(length = ColumnLengths.UUID)
    private String id;

    @Column(name = "trace_id", length = ColumnLengths.UUID, nullable = false)
    private String traceId;

    @Column(name = "chunk_id", length = ColumnLengths.UUID)
    private String chunkId;

    @Column(name = "score")
    private Double score;

    @Column(name = "l1_l2_l3_path", length = ColumnLengths.CATEGORY_PATH)
    private String path;

    @Column(name = "excerpt")
    private String excerpt;

    protected RagTraceChunkEntity() {
    }

    public RagTraceChunkEntity(
            String id,
            String traceId,
            String chunkId,
            Double score,
            String path,
            String excerpt
    ) {
        this.id = id;
        this.traceId = traceId;
        this.chunkId = chunkId;
        this.score = score;
        this.path = path;
        this.excerpt = excerpt;
    }

    public String getId() {
        return id;
    }

    public String getChunkId() {
        return chunkId;
    }

    public Double getScore() {
        return score;
    }

    public String getPath() {
        return path;
    }

    public String getExcerpt() {
        return excerpt;
    }
}
