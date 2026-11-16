-- =============================================================================
-- Migration: V2__seed_demo_tenant.sql
-- Description: Seed initial default demo tenants for development and testing
-- =============================================================================

INSERT INTO tenants (id, name, status, created_at, updated_at) VALUES
    ('tenant-alpha', 'Alpha Capital Partners', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('tenant-beta', 'Beta Global Investments', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('demo-tenant', 'Demo Financial Services', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
