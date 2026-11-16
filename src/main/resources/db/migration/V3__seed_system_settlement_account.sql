-- =============================================================================
-- Migration: V3__seed_system_settlement_account.sql
-- Description: Seed system settlement tenant and account for balanced deposits
-- =============================================================================

INSERT INTO tenants (id, name, status, created_at, updated_at) VALUES
    ('system', 'System Settlement & Clearing', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO accounts (id, tenant_id, currency, balance, status, version, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000000', 'system', 'USD', 0.0000, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
