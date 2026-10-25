-- ==============================================================================
-- V5__scope_invoice_idempotency_and_protect_ledgers.sql
-- Scope invoice idempotency to (tenant_id, idempotency_key),
-- protect financial ledgers from cascade deletions (ON DELETE RESTRICT),
-- and add request_hash column to idempotency_records.
-- ==============================================================================

-- 1. Scope invoice idempotency key to tenant
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_idempotency_key_key;
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS "CONSTRAINT_25F";
ALTER TABLE invoices ADD CONSTRAINT uq_invoices_tenant_idempotency UNIQUE (tenant_id, idempotency_key);

-- 2. Protect financial ledgers from cascade deletions
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_tenant_id_fkey;
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS fk_invoices_tenant;
ALTER TABLE invoices ADD CONSTRAINT fk_invoices_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;

ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_tenant_id_fkey;
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS fk_audit_logs_tenant;
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_logs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT;

-- 3. Add request_hash to idempotency_records for payload fingerprint verification
ALTER TABLE idempotency_records ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
