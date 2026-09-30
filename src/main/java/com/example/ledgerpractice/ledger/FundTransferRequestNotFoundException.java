package com.example.ledgerpractice.ledger;

public class FundTransferRequestNotFoundException extends RuntimeException {

    private FundTransferRequestNotFoundException(String message) {
        super(message);
    }

    public static FundTransferRequestNotFoundException byId(Long id) {
        return new FundTransferRequestNotFoundException("No FundTransferRequest found for id: " + id);
    }

    public static FundTransferRequestNotFoundException byExternalReferenceId(String externalReferenceId) {
        return new FundTransferRequestNotFoundException(
                "No FundTransferRequest found for externalReferenceId: " + externalReferenceId
                        + ". Verify the callback's reference matches a request created by this system before retrying.");
    }
}
