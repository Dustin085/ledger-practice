package com.example.ledgerpractice.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FundTransferRequestRepository extends JpaRepository<FundTransferRequest, Long> {

    Optional<FundTransferRequest> findByJournalEntryId(Long journalEntryId);
}
