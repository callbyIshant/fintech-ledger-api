-- =============================================================================
-- Migration: V1__init_ledger_schema.sql
-- Description: Core ledger schema with tenants, accounts, transactions, entries
-- =============================================================================

CREATE TABLE tenants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN'))
);

CREATE INDEX idx_accounts_tenant_id ON accounts(tenant_id);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    description VARCHAR(500),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalized_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_transactions_status CHECK (status IN ('DRAFT', 'POSTED'))
);

CREATE INDEX idx_transactions_tenant_id ON transactions(tenant_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at DESC);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    type VARCHAR(16) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ledger_entries_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_ledger_entries_type CHECK (type IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_entries_tenant_account_created ON ledger_entries(tenant_id, account_id, created_at DESC);
