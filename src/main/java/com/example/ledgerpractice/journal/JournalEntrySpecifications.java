package com.example.ledgerpractice.journal;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class JournalEntrySpecifications {
    public static Specification<JournalEntry> hasStatus(JournalEntryStatus status) {
        return (root, query, cb) ->
                status == null
                        ? null
                        : cb.equal(root.get("status"), status);
    }

    public static Specification<JournalEntry> hasKeyword(String keyword) {
        return (root, query, cb) ->
                keyword == null
                        ? null
                        : cb.like(root.get("description"), "%" + keyword + "%");
    }

    public static Specification<JournalEntry> entryDateFrom(LocalDate from) {
        return (root, query, cb) ->
                from == null
                        ? null
                        : cb.greaterThanOrEqualTo(root.get("entryDate"), from);
    }

    public static Specification<JournalEntry> entryDateTo(LocalDate to) {
        return (root, query, cb) ->
                to == null
                        ? null
                        : cb.lessThanOrEqualTo(root.get("entryDate"), to);
    }
}
