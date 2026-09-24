package com.example.ledgerpractice.ledger;

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

// 處理外部服務的非同步回覆，對應 Inbox pattern：靠 InboxEvent.externalEventId 的
// unique constraint 去重，同一個外部事件不會被處理兩次。這裡不需要 REQUIRES_NEW——
// 跟 TransferSubmissionService 不一樣，這裡每次只處理「一個」外部事件，不是在迴圈裡
// 逐筆處理一批，用預設的 REQUIRED 就夠。
@Service
@RequiredArgsConstructor
public class TransferResultService {

    private final FundTransferRequestRepository fundTransferRequestRepository;
    private final InboxEventRepository inboxEventRepository;
    private final ObjectMapper objectMapper;
    private final CompensationService compensationService;

    // 先鎖住 FundTransferRequest 再查 Inbox：同一個事件的重複 callback 會指向同一列，
    // 靠這把鎖把「檢查是否處理過」與「處理」序列化。如果先查再鎖，兩個併發的重複事件
    // 可能同時讀到「還沒處理」，輸的那個會在鎖之後因為狀態已改變而丟例外，而不是安靜略過。
    @Transactional
    public void confirm(String externalReferenceId, String externalEventId) {
        FundTransferRequest request = lockRequest(externalReferenceId);
        if (alreadyProcessed(externalEventId)) {
            return;
        }
        requireSubmitted(request, externalReferenceId);

        request.setStatus(TransferStatus.CONFIRMED);
        request.getJournalEntry().setStatus(JournalEntryStatus.POSTED);

        recordInboxEvent(externalEventId, "TransferConfirmed", request.getId());
    }

    @Transactional
    public void fail(String externalReferenceId, String externalEventId) {
        FundTransferRequest request = lockRequest(externalReferenceId);
        if (alreadyProcessed(externalEventId)) {
            return;
        }
        requireSubmitted(request, externalReferenceId);

        // 補償只動內部帳本：外部已經說這筆沒成功，代表錢根本沒有真的移動，
        // 不需要也不應該因此再對外發起任何請求，所以這裡不會產生新的 OutboxEvent。
        compensationService.compensate(request);

        recordInboxEvent(externalEventId, "TransferFailed", request.getId());
    }

    private boolean alreadyProcessed(String externalEventId) {
        return inboxEventRepository.findByExternalEventId(externalEventId).isPresent();
    }

    private FundTransferRequest lockRequest(String externalReferenceId) {
        return fundTransferRequestRepository.findByExternalReferenceIdForUpdate(externalReferenceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "FundTransferRequest not found for externalReferenceId: " + externalReferenceId));
    }

    private void requireSubmitted(FundTransferRequest request, String externalReferenceId) {
        if (request.getStatus() != TransferStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Expected SUBMITTED but was " + request.getStatus() + " for externalReferenceId " + externalReferenceId);
        }
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
