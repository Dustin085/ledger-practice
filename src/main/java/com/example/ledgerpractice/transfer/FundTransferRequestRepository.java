package com.example.ledgerpractice.transfer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FundTransferRequestRepository extends JpaRepository<FundTransferRequest, Long> {

    Optional<FundTransferRequest> findByJournalEntryId(Long journalEntryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FundTransferRequest f where f.id = :id")
    Optional<FundTransferRequest> findByIdForUpdate(@Param("id") Long id);
}
