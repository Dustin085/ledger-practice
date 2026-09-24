package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.account.AccountRepository;
import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.outbox.InboxEventRepository;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.JournalLineRequest;
import com.example.ledgerpractice.transfer.TransferStatus;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TransferResultConcurrencyTest {

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
    private InboxEventRepository inboxEventRepository;

    @RepeatedTest(10)
    void sameEventDeliveredConcurrentlyIsAppliedOnce() throws Exception {
        Long payroll = accountRepository.findByCode("5101").orElseThrow().getId();
        Long bank = accountRepository.findByCode("1102").orElseThrow().getId();
        JournalEntry entry = ledgerService.initiateTransfer(LocalDate.now(), "併發 callback", List.of(
                new JournalLineRequest(payroll, BigDecimal.valueOf(100), BigDecimal.ZERO, ""),
                new JournalLineRequest(bank, BigDecimal.ZERO, BigDecimal.valueOf(100), "")), "");
        Long requestId = fundTransferRequestRepository.findByJournalEntryId(entry.getId()).orElseThrow().getId();
        transferSubmissionService.submit(requestId);
        String ref = fundTransferRequestRepository.findByJournalEntryId(entry.getId()).orElseThrow().getExternalReferenceId();

        String eventId = UUID.randomUUID().toString();
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    transferResultService.confirm(ref, eventId);
                } catch (Throwable t) {
                    failures.add(t);
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        FundTransferRequest after = fundTransferRequestRepository.findByJournalEntryId(entry.getId()).orElseThrow();
        assertThat(failures).isEmpty();
        assertThat(after.getStatus()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(inboxEventRepository.findByExternalEventId(eventId)).isPresent();
    }
}
