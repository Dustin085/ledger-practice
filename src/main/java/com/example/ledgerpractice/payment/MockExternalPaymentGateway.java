package com.example.ledgerpractice.payment;

import com.example.ledgerpractice.transfer.FundTransferRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockExternalPaymentGateway implements ExternalPaymentGateway {

    // 固定用可控制的字串觸發失敗，不用隨機機率——demo 補償路徑時才能穩定重現，
    // 不用一直重試賭運氣。
    private static final String FAIL_TRIGGER = "FAIL";

    @Override
    public String submit(FundTransferRequest request) {
        String counterparty = request.getExternalCounterparty();
        if (counterparty != null && counterparty.toUpperCase().contains(FAIL_TRIGGER)) {
            throw new PaymentGatewaySubmissionException(
                    "Mock gateway rejected submission for counterparty: " + counterparty);
        }
        return "MOCK-" + UUID.randomUUID();
    }
}
