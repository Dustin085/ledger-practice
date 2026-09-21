package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.journal.JournalEntryLine;
import com.example.ledgerpractice.journal.JournalEntryRepository;
import com.example.ledgerpractice.journal.JournalEntryStatus;
import com.example.ledgerpractice.outbox.InboxEvent;
import com.example.ledgerpractice.outbox.InboxEventRepository;
import com.example.ledgerpractice.outbox.TransferResultPayload;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.TransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;

// 處理外部服務的非同步回覆，對應 Inbox pattern：靠 InboxEvent.externalEventId 的
// unique constraint 去重，同一個外部事件不會被處理兩次。這裡不需要 REQUIRES_NEW——
// 跟 TransferSubmissionService 不一樣，這裡每次只處理「一個」外部事件，不是在迴圈裡
// 逐筆處理一批，用預設的 REQUIRED 就夠。
@Service
@RequiredArgsConstructor
public class TransferResultService {

    private final FundTransferRequestRepository fundTransferRequestRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final InboxEventRepository inboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void confirm(Long fundTransferRequestId, String externalEventId) {
        if (alreadyProcessed(externalEventId)) {
            return;
        }

        FundTransferRequest request = lockSubmittedRequest(fundTransferRequestId);

        request.setStatus(TransferStatus.CONFIRMED);
        request.getJournalEntry().setStatus(JournalEntryStatus.POSTED);

        recordInboxEvent(externalEventId, "TransferConfirmed", fundTransferRequestId);
    }

    @Transactional
    public void fail(Long fundTransferRequestId, String externalEventId) {
        if (alreadyProcessed(externalEventId)) {
            return;
        }

        FundTransferRequest request = lockSubmittedRequest(fundTransferRequestId);
        JournalEntry original = request.getJournalEntry();

        request.setStatus(TransferStatus.COMPENSATED);
        original.setStatus(JournalEntryStatus.REVERSED);

        journalEntryRepository.save(buildReversalEntry(original));

        recordInboxEvent(externalEventId, "TransferFailed", fundTransferRequestId);
    }

    // 補償只動內部帳本：外部已經說這筆沒成功，代表錢根本沒有真的移動，
    // 不需要也不應該因此再對外發起任何請求，所以這裡不會產生新的 OutboxEvent。
    private JournalEntry buildReversalEntry(JournalEntry original) {
        JournalEntry reversal = JournalEntry.builder()
                .entryDate(LocalDate.now())
                .description("沖銷：" + original.getDescription())
                .reversalOfEntryId(original.getId())
                .status(JournalEntryStatus.POSTED)
                .build();

        for (JournalEntryLine line : original.getLines()) {
            JournalEntryLine reversalLine = JournalEntryLine.builder()
                    .account(line.getAccount())
                    .debitAmount(line.getCreditAmount())
                    .creditAmount(line.getDebitAmount())
                    .memo("沖銷：" + (line.getMemo() == null ? "" : line.getMemo()))
                    .build();
            reversal.addLine(reversalLine);
        }

        return reversal;
    }

    private boolean alreadyProcessed(String externalEventId) {
        return inboxEventRepository.findByExternalEventId(externalEventId).isPresent();
    }

    private FundTransferRequest lockSubmittedRequest(Long fundTransferRequestId) {
        FundTransferRequest request = fundTransferRequestRepository.findByIdForUpdate(fundTransferRequestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "FundTransferRequest not found: " + fundTransferRequestId));
        if (request.getStatus() != TransferStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Expected SUBMITTED but was " + request.getStatus() + " for id " + fundTransferRequestId);
        }
        return request;
    }

    private void recordInboxEvent(String externalEventId, String eventType, Long fundTransferRequestId) {
        TransferResultPayload payload = new TransferResultPayload(fundTransferRequestId, eventType);
        InboxEvent event = InboxEvent.builder()
                .externalEventId(externalEventId)
                .eventType(eventType)
                .payload(objectMapper.writeValueAsString(payload))
                .processedAt(Instant.now())
                .build();
        inboxEventRepository.save(event);
    }
}
