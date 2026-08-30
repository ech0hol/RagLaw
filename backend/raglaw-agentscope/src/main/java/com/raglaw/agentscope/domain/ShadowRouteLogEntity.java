package com.raglaw.agentscope.domain;

import com.raglaw.common.jpa.ColumnLengths;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_shadow_route_log")
public class ShadowRouteLogEntity {

    @Id
    @Column(length = ColumnLengths.UUID)
    private String id;

    @Column(name = "trace_id", length = ColumnLengths.UUID, nullable = false)
    private String traceId;

    @Column(name = "shadow_type", length = 32, nullable = false)
    private String shadowType;

    @Column(name = "user_value", length = 512)
    private String userValue;

    @Column(name = "system_value", length = 512)
    private String systemValue;

    @Column(name = "hit")
    private Boolean hit;

    @Column(name = "route_rank")
    private Integer rank;

    @Column(name = "confidence_json", columnDefinition = "TEXT")
    private String confidenceJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getShadowType() {
        return shadowType;
    }

    public void setShadowType(String shadowType) {
        this.shadowType = shadowType;
    }

    public String getUserValue() {
        return userValue;
    }

    public void setUserValue(String userValue) {
        this.userValue = userValue;
    }

    public String getSystemValue() {
        return systemValue;
    }

    public void setSystemValue(String systemValue) {
        this.systemValue = systemValue;
    }

    public Boolean getHit() {
        return hit;
    }

    public void setHit(Boolean hit) {
        this.hit = hit;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public String getConfidenceJson() {
        return confidenceJson;
    }

    public void setConfidenceJson(String confidenceJson) {
        this.confidenceJson = confidenceJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
