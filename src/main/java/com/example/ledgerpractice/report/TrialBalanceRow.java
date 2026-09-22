package com.example.ledgerpractice.report;

import com.example.ledgerpractice.account.AccountType;

import java.math.BigDecimal;

public record TrialBalanceRow(
        String accountCode,
        String accountName,
        AccountType accountType,
        BigDecimal totalDebit,
        BigDecimal totalCredit
) {
}
