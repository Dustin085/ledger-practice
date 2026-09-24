package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.payment.ExternalPaymentGateway;
import com.example.ledgerpractice.payment.ExternalTransferStatus;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.TransferStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// 對帳：外部的 callback 可能遺失，讓 FundTransferRequest 永遠停在 SUBMITTED。
// 這裡主動去問外部「這筆現在是什麼狀態」，再走跟 callback 完全相同的 confirm/fail。
//
// 刻意「不加 @Transactional」：查詢外部是網路呼叫，不能在持有資料庫鎖的交易裡做，
// 否則外部一慢，整列 FundTransferRequest 都被鎖住。confirm/fail 會自己鎖列並重新檢查狀態。
@Service
@Slf4j
public class TransferReconciliationService {

    private static final String EVENT_ID_PREFIX = "reconcile-";

    private final FundTransferRequestRepository fundTransferRequestRepository;
    private final ExternalPaymentGateway externalPaymentGateway;
    private final TransferResultService transferResultService;
    private final Duration staleAfter;

    public TransferReconciliationService(
            FundTransferRequestRepository fundTransferRequestRepository,
            ExternalPaymentGateway externalPaymentGateway,
            TransferResultService transferResultService,
            @Value("${reconciliation.stale-after}") Duration staleAfter) {
        this.fundTransferRequestRepository = fundTransferRequestRepository;
        this.externalPaymentGateway = externalPaymentGateway;
        this.transferResultService = transferResultService;
        this.staleAfter = staleAfter;
    }

    public void reconcileStaleTransfers() {
        Instant cutoff = Instant.now().minus(staleAfter);
        List<FundTransferRequest> staleRequests =
                fundTransferRequestRepository.findByStatusAndLastAttemptAtBefore(TransferStatus.SUBMITTED, cutoff);
        for (FundTransferRequest request : staleRequests) {
            try {
                reconcile(request.getExternalReferenceId());
            } catch (RuntimeException e) {
                // 單筆失敗不能中斷整批，下一輪會再試。
                log.error("對帳失敗，externalReferenceId={}", request.getExternalReferenceId(), e);
            }
        }
    }

    private void reconcile(String externalReferenceId) {
        ExternalTransferStatus externalStatus = externalPaymentGateway.queryStatus(externalReferenceId);
        // 事件 id 依 reference 固定，同一筆重複對帳不會重複處理（Inbox 去重）。
        String eventId = EVENT_ID_PREFIX + externalReferenceId;
        switch (externalStatus) {
            case CONFIRMED -> transferResultService.confirm(externalReferenceId, eventId);
            case FAILED -> transferResultService.fail(externalReferenceId, eventId);
            // 外部說「還在處理」或「查無此單」都代表我們不知道錢到底有沒有動：
            // 絕對不能自動補償（可能錢已經轉出去了），只記錄，等下一輪或人工介入。
            case PENDING -> log.info("對帳：外部仍在處理中，externalReferenceId={}", externalReferenceId);
            case NOT_FOUND -> log.warn("對帳：外部查無此單，需人工確認，externalReferenceId={}", externalReferenceId);
        }
    }
}
