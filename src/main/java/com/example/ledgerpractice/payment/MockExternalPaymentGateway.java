package com.example.ledgerpractice.payment;

import com.example.ledgerpractice.transfer.FundTransferRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockExternalPaymentGateway implements ExternalPaymentGateway {

    // 固定用可控制的字串觸發行為，不用隨機機率——demo 各條路徑時才能穩定重現，
    // 不用一直重試賭運氣。
    private static final String FAIL_TRIGGER = "FAIL";
    // 模擬「外部其實已經成功，但 callback 遺失」：對帳查詢會回 CONFIRMED。
    private static final String LOST_TRIGGER = "LOST";
    // 模擬「外部其實已經失敗，但 callback 遺失」：對帳查詢會回 FAILED。
    private static final String DECLINE_TRIGGER = "DECLINE";

    // 只是模擬用：真實的外部系統（綠界、銀行）有自己的資料庫，這裡用記憶體假裝它。
    // 它只記「自己收到過什麼」，完全不碰我們的資料表；應用程式重啟就清空，
    // 所以重啟前送出的 reference 之後查詢會是 NOT_FOUND，這在真實系統不會發生。
    private final Map<String, String> receivedCounterpartyByReference = new ConcurrentHashMap<>();

    @Override
    public String submit(FundTransferRequest request) {
        String counterparty = request.getExternalCounterparty();
        if (containsTrigger(counterparty, FAIL_TRIGGER)) {
            throw new PaymentGatewaySubmissionException(
                    "Mock gateway rejected submission for counterparty: " + counterparty);
        }
        String externalReferenceId = "MOCK-" + UUID.randomUUID();
        receivedCounterpartyByReference.put(externalReferenceId, counterparty == null ? "" : counterparty);
        return externalReferenceId;
    }

    @Override
    public ExternalTransferStatus queryStatus(String externalReferenceId) {
        String counterparty = receivedCounterpartyByReference.get(externalReferenceId);
        if (counterparty == null) {
            return ExternalTransferStatus.NOT_FOUND;
        }
        if (containsTrigger(counterparty, LOST_TRIGGER)) {
            return ExternalTransferStatus.CONFIRMED;
        }
        if (containsTrigger(counterparty, DECLINE_TRIGGER)) {
            return ExternalTransferStatus.FAILED;
        }
        return ExternalTransferStatus.PENDING;
    }

    private boolean containsTrigger(String counterparty, String trigger) {
        return counterparty != null && counterparty.toUpperCase().contains(trigger);
    }
}
