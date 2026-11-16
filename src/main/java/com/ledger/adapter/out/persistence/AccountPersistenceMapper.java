package com.ledger.adapter.out.persistence;

import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import org.springframework.stereotype.Component;

import java.util.Currency;

@Component
public class AccountPersistenceMapper {

    public Account toDomain(AccountJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Currency currency = Currency.getInstance(entity.getCurrency());
        Money balance = Money.of(entity.getBalance(), currency);

        return new Account(
                AccountId.of(entity.getId()),
                TenantId.of(entity.getTenantId()),
                currency,
                balance,
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public AccountJpaEntity toJpaEntity(Account domain) {
        if (domain == null) {
            return null;
        }
        return new AccountJpaEntity(
                domain.getId().value(),
                domain.getTenantId().value(),
                domain.getCurrency().getCurrencyCode(),
                domain.getBalance().amount(),
                domain.getStatus(),
                domain.getVersion(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
