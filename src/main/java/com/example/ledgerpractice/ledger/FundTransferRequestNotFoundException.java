package com.example.ledgerpractice.ledger;

public class FundTransferRequestNotFoundException extends RuntimeException {

    private FundTransferRequestNotFoundException(String message) {
        super(message);
    }

    public static FundTransferRequestNotFoundException byId(Long id) {
        return new FundTransferRequestNotFoundException("找不到 id 為: " + id + " 的金流請求");
    }

    public static FundTransferRequestNotFoundException byExternalReferenceId(String externalReferenceId) {
        return new FundTransferRequestNotFoundException(
                "找不到 externalReferenceId 為: " + externalReferenceId + " 的金流請求");
    }
}
