-- ==============================================================================
-- V2__seed_plans.sql
-- Default Subscription Plans Seed Data
-- ==============================================================================

INSERT INTO plans (name, monthly_price, max_users, max_api_calls) VALUES
('FREE', 0.00, 5, 1000),
('PRO', 49.00, 25, 50000),
('ENTERPRISE', 299.00, 9999, 1000000);
