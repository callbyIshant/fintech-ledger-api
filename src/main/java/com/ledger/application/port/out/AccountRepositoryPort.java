package com.ledger.application.port.out;

import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountRepositoryPort {

    Account save(Account account);

    Optional<Account> findById(AccountId accountId);

    Optional<Account> findByTenantIdAndAccountId(TenantId tenantId, AccountId accountId);

    List<Account> findByTenantIdAndAccountIdsForUpdate(TenantId tenantId, Collection<AccountId> accountIds);

    boolean existsById(AccountId accountId);
}
