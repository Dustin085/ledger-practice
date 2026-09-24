package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.account.AccountRepository;
import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.journal.JournalEntryRepository;
import com.example.ledgerpractice.journal.JournalEntryStatus;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.JournalLineRequest;
import com.example.ledgerpractice.transfer.TransferStatus;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 端對端 Saga 測試：initiateTransfer -> submit -> confirm/fail 整條鏈路串起來測，
// 用真正的 bean 跟真正的 H2，不 mock 任何一段。
//
// 故意不加 @Transactional：submit() 用 REQUIRES_NEW 開獨立交易，需要看到
// initiateTransfer() 已經真正 commit 的資料。如果測試方法包一層 rollback 用的
// @Transactional，initiateTransfer() 的寫入會停在外層測試交易裡沒有 commit，
// submit() 那個獨立連線的新交易在 READ_COMMITTED 下看不到，找不到 request。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TransferSagaIntegrationTest {

    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private TransferSubmissionService transferSubmissionService;
    @Autowired
    private TransferResultService transferResultService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private FundTransferRequestRepository fundTransferRequestRepository;
    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private Long payrollAccountId;
    private Long bankAccountId;

    // 直接沿用 AccountSeeder 種好的科目，不用自己另外建，避免撞到 code 的
    // unique constraint（這個測試沒有 @Transactional 幫忙 rollback，資料是真的留著的）。
    @BeforeEach
    void setUp() {
        payrollAccountId = accountRepository.findByCode("5101").orElseThrow().getId();
        bankAccountId = accountRepository.findByCode("1102").orElseThrow().getId();
    }

    @Test
    void happyPathConfirmedByExternalSystem() {
        List<JournalLineRequest> lines = createPaySalaryLines();
        JournalEntry journalEntry = ledgerService.initiateTransfer(LocalDate.now(), "支付薪水", lines, "");
        FundTransferRequest fundTransferRequest = fundTransferRequestRepository.findByJournalEntryId(journalEntry.getId()).orElseThrow();

        transferSubmissionService.submit(fundTransferRequest.getId());
        FundTransferRequest afterSubmit = fundTransferRequestRepository.findByJournalEntryId(journalEntry.getId()).orElseThrow();
        assertThat(afterSubmit.getStatus()).isEqualTo(TransferStatus.SUBMITTED);

        transferResultService.confirm(afterSubmit.getExternalReferenceId(), UUID.randomUUID().toString());
        FundTransferRequest afterConfirm = fundTransferRequestRepository.findByJournalEntryId(journalEntry.getId()).orElseThrow();
        assertThat(afterConfirm.getStatus()).isEqualTo(TransferStatus.CONFIRMED);

        JournalEntry postedEntry = journalEntryRepository.findById(journalEntry.getId()).orElseThrow();
        assertThat(postedEntry.getStatus()).isEqualTo(JournalEntryStatus.POSTED);
    }


    @Test
    void externalSystemExplicitlyFailsTriggersCompensationAndIsIdempotent() {
        List<JournalLineRequest> lines = createPaySalaryLines();
        JournalEntry journalEntry = ledgerService.initiateTransfer(LocalDate.now(), "支付薪水（外部回覆失敗）", lines, "");
        FundTransferRequest fundTransferRequest = fundTransferRequestRepository.findByJournalEntryId(journalEntry.getId()).orElseThrow();

        transferSubmissionService.submit(fundTransferRequest.getId());
        FundTransferRequest afterSubmit = fundTransferRequestRepository.findByJournalEntryId(journalEntry.getId()).orElseThrow();
        assertThat(afterSubmit.getStatus()).isEqualTo(TransferStatus.SUBMITTED);

        String externalEventId = UUID.randomUUID().toString();
        transferResultService.fail(afterSubmit.getExternalReferenceId(), externalEventId);

        FundTransferRequest afterFail = fundTransferRequestRepository.findByJournalEntryId(journalEntry.getId()).orElseThrow();
        assertThat(afterFail.getStatus()).isEqualTo(TransferStatus.COMPENSATED);

        JournalEntry reversedEntry = journalEntryRepository.findById(journalEntry.getId()).orElseThrow();
        assertThat(reversedEntry.getStatus()).isEqualTo(JournalEntryStatus.REVERSED);

        List<JournalEntry> reversals = journalEntryRepository.findAllByReversalOfEntryId(journalEntry.getId());
        assertThat(reversals).hasSize(1);
        assertThat(reversals.getFirst().getStatus()).isEqualTo(JournalEntryStatus.POSTED);

        // 同一個 externalEventId 再送一次，Inbox 去重應該讓這次呼叫直接沒動作，
        // 不會多產生第二筆沖銷分錄。
        transferResultService.fail(afterSubmit.getExternalReferenceId(), externalEventId);

        List<JournalEntry> reversalsAfterDuplicateEvent = journalEntryRepository.findAllByReversalOfEntryId(journalEntry.getId());
        assertThat(reversalsAfterDuplicateEvent).hasSize(1);
    }

    @Test
    void retriesExhaustedGivesUpAndCompensatesAutomatically() {
        Integer maxAttempts = (Integer) ReflectionTestUtils.getField(transferSubmissionService, "MAX_ATTEMPTS");
        assertThat(maxAttempts).isNotNull();

        List<JournalLineRequest> lines = createPaySalaryLines();
        JournalEntry journalEntry = ledgerService.initiateTransfer(
                LocalDate.now(), "支付薪水（外部一直拒絕）", lines, "employee-FAIL");
        FundTransferRequest fundTransferRequest = fundTransferRequestRepository.findByJournalEntryId(journalEntry.getId()).orElseThrow();

        for (int attempt = 1; attempt < maxAttempts; attempt++) {
            SubmitOutcome outcome = transferSubmissionService.submit(fundTransferRequest.getId());
            assertThat(outcome).isEqualTo(SubmitOutcome.FAILED);
        }
        SubmitOutcome lastOutcome = transferSubmissionService.submit(fundTransferRequest.getId());
        assertThat(lastOutcome).isEqualTo(SubmitOutcome.GAVE_UP);

        FundTransferRequest afterGaveUp = fundTransferRequestRepository.findByJournalEntryId(journalEntry.getId()).orElseThrow();
        assertThat(afterGaveUp.getStatus()).isEqualTo(TransferStatus.COMPENSATED);

        JournalEntry reversedEntry = journalEntryRepository.findById(journalEntry.getId()).orElseThrow();
        assertThat(reversedEntry.getStatus()).isEqualTo(JournalEntryStatus.REVERSED);

        List<JournalEntry> reversals = journalEntryRepository.findAllByReversalOfEntryId(journalEntry.getId());
        assertThat(reversals).hasSize(1);
        assertThat(reversals.getFirst().getStatus()).isEqualTo(JournalEntryStatus.POSTED);
    }

    private @NonNull List<JournalLineRequest> createPaySalaryLines() {
        List<JournalLineRequest> lines = new ArrayList<>();
        lines.add(
                new JournalLineRequest(
                        payrollAccountId,
                        BigDecimal.valueOf(1000),
                        BigDecimal.ZERO,
                        ""
                )
        );
        lines.add(
                new JournalLineRequest(
                        bankAccountId,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(1000),
                        ""
                )
        );
        return lines;
    }
}
