package com.example.ledgerpractice.payment;

import com.example.ledgerpractice.transfer.FundTransferRequest;

public interface ExternalPaymentGateway {
    String submit(FundTransferRequest request);
}
