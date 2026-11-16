package com.ledger.adapter.out.persistence;

import com.ledger.application.port.out.AccountRepositoryPort;
import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.TenantId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class AccountPersistenceAdapter implements AccountRepositoryPort {

    private final SpringDataAccountRepository springDataAccountRepository;
    private final AccountPersistenceMapper accountPersistenceMapper;

    public AccountPersistenceAdapter(SpringDataAccountRepository springDataAccountRepository,
                                     AccountPersistenceMapper accountPersistenceMapper) {
        this.springDataAccountRepository = springDataAccountRepository;
        this.accountPersistenceMapper = accountPersistenceMapper;
    }

    @Override
    public Account save(Account account) {
        Objects.requireNonNull(account, "Account cannot be null");
        AccountJpaEntity entity = accountPersistenceMapper.toJpaEntity(account);
        if (account.getVersion() == null) {
            entity.setNew(true);
        } else {
            entity.setNew(false);
        }
        AccountJpaEntity saved = springDataAccountRepository.save(entity);
        return accountPersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Account> findById(AccountId accountId) {
        Objects.requireNonNull(accountId, "AccountId cannot be null");
        return springDataAccountRepository.findById(accountId.value())
                .map(accountPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Account> findByTenantIdAndAccountId(TenantId tenantId, AccountId accountId) {
        Objects.requireNonNull(tenantId, "TenantId cannot be null");
        Objects.requireNonNull(accountId, "AccountId cannot be null");
        return springDataAccountRepository.findByTenantIdAndId(tenantId.value(), accountId.value())
                .map(accountPersistenceMapper::toDomain);
    }

    @Override
    public List<Account> findByTenantIdAndAccountIdsForUpdate(TenantId tenantId, Collection<AccountId> accountIds) {
        Objects.requireNonNull(tenantId, "TenantId cannot be null");
        Objects.requireNonNull(accountIds, "AccountIds cannot be null");
        if (accountIds.isEmpty()) {
            return List.of();
        }
        List<UUID> uuids = accountIds.stream().map(AccountId::value).toList();
        List<AccountJpaEntity> entities = springDataAccountRepository
                .findByTenantIdAndIdInOrderByIdAscForUpdate(tenantId.value(), uuids);
        return entities.stream().map(accountPersistenceMapper::toDomain).toList();
    }

    @Override
    public boolean existsById(AccountId accountId) {
        Objects.requireNonNull(accountId, "AccountId cannot be null");
        return springDataAccountRepository.existsById(accountId.value());
    }
}
