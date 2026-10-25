-- ==============================================================================
-- V4__fix_tenant_isolation_and_indexes.sql
-- Fix cross-tenant user email collision, enforce global email uniqueness,
-- drop duplicate idempotency index, and index foreign key subscriptions(plan_id).
-- ==============================================================================

-- 1. Enforce global uniqueness on users(email) to eliminate cross-tenant collision / DoS
ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_tenant_user_email;
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);

-- 2. Drop duplicate redundant B-Tree index on invoices(idempotency_key)
DROP INDEX IF EXISTS idx_invoices_idempotency;

-- 3. Add index on foreign key subscriptions(plan_id) for join performance
CREATE INDEX IF NOT EXISTS idx_subscriptions_plan ON subscriptions(plan_id);
