package com.example.ledgerpractice.journal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, Long> {
    @Query("""
            SELECT new com.example.ledgerpractice.journal.JournalEntryLineSummary(
                l.id,
                je.entryDate,
                l.debitAmount,
                l.creditAmount,
                l.memo
            )
            FROM JournalEntryLine l
            JOIN  l.journalEntry je
            WHERE l.account.id = :id
            ORDER BY je.entryDate, l.id
            """)
    public List<JournalEntryLineSummary> findSummaryByAccountId(Long id);
}
