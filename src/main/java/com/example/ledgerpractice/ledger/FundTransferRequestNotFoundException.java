package com.example.ledgerpractice.ledger;

public class FundTransferRequestNotFoundException extends RuntimeException {
    public FundTransferRequestNotFoundException(Long requestId) {
        super("找不到 id 為: " + requestId + " 的金流請求");
    }
}
