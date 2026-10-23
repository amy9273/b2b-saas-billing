-- ==============================================================================
-- V3__usage_metering_and_idempotency.sql
-- API Usage Metering and Idempotency Records Schema
-- ==============================================================================

-- 1. API Usage Metering Table
CREATE TABLE IF NOT EXISTS api_usage (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    call_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_api_usage_tenant_period UNIQUE (tenant_id, period_start, period_end)
);

-- 2. Idempotency Records Table
CREATE TABLE IF NOT EXISTS idempotency_records (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    response_status INT,
    response_body TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING', -- PROCESSING, COMPLETED, FAILED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_idempotency_tenant_key UNIQUE (tenant_id, idempotency_key)
);

-- Indexes for high-throughput lookup
CREATE INDEX IF NOT EXISTS idx_api_usage_tenant ON api_usage(tenant_id, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_idempotency_lookup ON idempotency_records(tenant_id, idempotency_key);
