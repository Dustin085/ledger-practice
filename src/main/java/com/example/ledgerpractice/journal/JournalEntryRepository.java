package com.example.ledgerpractice.journal;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByStatus(JournalEntryStatus status, Sort sort);

    @Query("SELECT DISTINCT je FROM JournalEntry je " +
            "LEFT JOIN FETCH je.lines l " +
            "LEFT JOIN FETCH l.account a " +
            "WHERE je.id = :id")
    Optional<JournalEntry> findWithLinesById(@Param("id") Long id);
}
