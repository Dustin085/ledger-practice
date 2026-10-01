package com.example.ledgerpractice.journal;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JournalEntryLineSummary(
        Long id,
        LocalDate entryDate,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String memo
) {
}
