package com.example.ledgerpractice.outbox;

import java.math.BigDecimal;

public record FundTransferRequestedPayload(
        Long transferRequestId,
        Long journalEntryId,
        BigDecimal amount,
        String externalCounterparty) {
}
