package com.example.ledgerpractice.config;

import com.example.ledgerpractice.account.Account;
import com.example.ledgerpractice.account.AccountRepository;
import com.example.ledgerpractice.account.AccountType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AccountSeeder implements CommandLineRunner {

    private final AccountRepository accountRepository;

    public AccountSeeder(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(String... args) {
        if (accountRepository.count() > 0) {
            return;
        }

        accountRepository.saveAll(java.util.List.of(
                Account.builder().code("1101").name("現金").type(AccountType.ASSET).build(),
                Account.builder().code("1102").name("銀行存款").type(AccountType.ASSET)
                        .externalSettlement(true).build(),
                Account.builder().code("1103").name("應收帳款").type(AccountType.ASSET).build(),
                Account.builder().code("2101").name("應付帳款").type(AccountType.LIABILITY).build(),
                Account.builder().code("3101").name("股本").type(AccountType.EQUITY).build(),
                Account.builder().code("4101").name("服務收入").type(AccountType.REVENUE).build(),
                Account.builder().code("5101").name("薪資費用").type(AccountType.EXPENSE).build()
        ));
    }
}
