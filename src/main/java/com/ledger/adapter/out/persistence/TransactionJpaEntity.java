package com.ledger.adapter.out.persistence;

import com.ledger.core.domain.TransactionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class TransactionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TransactionStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LedgerEntryJpaEntity> entries = new ArrayList<>();

    protected TransactionJpaEntity() {
    }

    public TransactionJpaEntity(UUID id, String tenantId, String description, TransactionStatus status, Long version, Instant createdAt, Instant finalizedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.description = description;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.finalizedAt = finalizedAt;
    }

    public void addEntry(LedgerEntryJpaEntity entry) {
        entries.add(entry);
        entry.setTransaction(this);
    }

    public void removeEntry(LedgerEntryJpaEntity entry) {
        entries.remove(entry);
        entry.setTransaction(null);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
    }

    public List<LedgerEntryJpaEntity> getEntries() {
        return entries;
    }

    public void setEntries(List<LedgerEntryJpaEntity> entries) {
        this.entries = entries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionJpaEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
