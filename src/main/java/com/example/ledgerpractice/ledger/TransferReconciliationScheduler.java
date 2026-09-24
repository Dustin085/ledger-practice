package com.example.ledgerpractice.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 只負責「定時觸發」，對帳邏輯在 TransferReconciliationService，測試才能直接呼叫它而不用等排程。
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class TransferReconciliationScheduler {

    private final TransferReconciliationService transferReconciliationService;

    @Scheduled(fixedDelayString = "${reconciliation.interval-ms}")
    public void run() {
        transferReconciliationService.reconcileStaleTransfers();
    }
}
