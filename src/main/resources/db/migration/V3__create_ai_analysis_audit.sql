CREATE TABLE ai_analysis_audit (
    id                  BIGSERIAL PRIMARY KEY,
    requested_by        VARCHAR(255) NOT NULL,
    requested_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    model               VARCHAR(255) NOT NULL,
    fleet_health_score  INTEGER,
    recommendation_count INTEGER,
    execution_time_ms   BIGINT
);

CREATE INDEX idx_ai_audit_requested_at ON ai_analysis_audit (requested_at DESC);
CREATE INDEX idx_ai_audit_requested_by ON ai_analysis_audit (requested_by);
