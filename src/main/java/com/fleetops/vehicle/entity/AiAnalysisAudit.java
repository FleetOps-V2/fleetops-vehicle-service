package com.fleetops.vehicle.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_analysis_audit")
public class AiAnalysisAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "model", nullable = false, length = 255)
    private String model;

    @Column(name = "fleet_health_score")
    private Integer fleetHealthScore;

    @Column(name = "recommendation_count")
    private Integer recommendationCount;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getFleetHealthScore() { return fleetHealthScore; }
    public void setFleetHealthScore(Integer fleetHealthScore) { this.fleetHealthScore = fleetHealthScore; }

    public Integer getRecommendationCount() { return recommendationCount; }
    public void setRecommendationCount(Integer recommendationCount) { this.recommendationCount = recommendationCount; }

    public Long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
}
