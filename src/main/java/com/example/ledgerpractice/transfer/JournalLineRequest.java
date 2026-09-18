package com.example.ledgerpractice.transfer;

import java.math.BigDecimal;

public record JournalLineRequest(
        Long accountId,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String memo) {
}
