package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.account.AccountRepository;
import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.journal.JournalEntryRepository;
import com.example.ledgerpractice.journal.JournalEntryStatus;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.JournalLineRequest;
import com.example.ledgerpractice.transfer.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 用真的 bean 與 H2 跑完整對帳流程；application-test.yml 把 stale-after 設成 0，
// 所以剛送出的轉帳立刻就算「逾時」，不需要等或改資料庫時間。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TransferReconciliationIntegrationTest {

    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private TransferSubmissionService transferSubmissionService;
    @Autowired
    private TransferResultService transferResultService;
    @Autowired
    private TransferReconciliationService transferReconciliationService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private FundTransferRequestRepository fundTransferRequestRepository;
    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private Long payrollAccountId;
    private Long bankAccountId;

    @BeforeEach
    void setUp() {
        payrollAccountId = accountRepository.findByCode("5101").orElseThrow().getId();
        bankAccountId = accountRepository.findByCode("1102").orElseThrow().getId();
    }

    private JournalEntry submittedTransfer(String counterparty) {
        JournalEntry entry = ledgerService.initiateTransfer(LocalDate.now(), "對帳測試：" + counterparty, List.of(
                new JournalLineRequest(payrollAccountId, BigDecimal.valueOf(100), BigDecimal.ZERO, ""),
                new JournalLineRequest(bankAccountId, BigDecimal.ZERO, BigDecimal.valueOf(100), "")), counterparty);
        Long requestId = fundTransferRequestRepository.findByJournalEntryId(entry.getId()).orElseThrow().getId();
        transferSubmissionService.submit(requestId);
        return entry;
    }

    private FundTransferRequest reload(JournalEntry entry) {
        return fundTransferRequestRepository.findByJournalEntryId(entry.getId()).orElseThrow();
    }

    @Test
    void lostCallbackForSuccessfulTransferIsRecoveredAsConfirmed() {
        JournalEntry entry = submittedTransfer("emp-LOST");
        assertThat(reload(entry).getStatus()).isEqualTo(TransferStatus.SUBMITTED);

        transferReconciliationService.reconcileStaleTransfers();

        assertThat(reload(entry).getStatus()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(journalEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(JournalEntryStatus.POSTED);
    }

    @Test
    void lostCallbackForDeclinedTransferIsRecoveredAsCompensated() {
        JournalEntry entry = submittedTransfer("emp-DECLINE");

        transferReconciliationService.reconcileStaleTransfers();

        assertThat(reload(entry).getStatus()).isEqualTo(TransferStatus.COMPENSATED);
        assertThat(journalEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(JournalEntryStatus.REVERSED);
        assertThat(journalEntryRepository.findAllByReversalOfEntryId(entry.getId())).hasSize(1);
    }

    @Test
    void stillPendingAtExternalIsLeftUntouched() {
        JournalEntry entry = submittedTransfer("emp-normal");

        transferReconciliationService.reconcileStaleTransfers();

        assertThat(reload(entry).getStatus()).isEqualTo(TransferStatus.SUBMITTED);
        assertThat(journalEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(JournalEntryStatus.PENDING);
    }

    @Test
    void reconcilingTwiceAndThenALateRealCallbackAllSucceedWithoutDoubleProcessing() {
        JournalEntry entry = submittedTransfer("emp-LOST-2");
        String ref = reload(entry).getExternalReferenceId();

        transferReconciliationService.reconcileStaleTransfers();
        transferReconciliationService.reconcileStaleTransfers();
        transferResultService.confirm(ref, "late-real-callback-" + ref);

        assertThat(reload(entry).getStatus()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(journalEntryRepository.findAllByReversalOfEntryId(entry.getId())).isEmpty();
    }
}
