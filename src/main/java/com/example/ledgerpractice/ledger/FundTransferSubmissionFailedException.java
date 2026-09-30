package com.example.ledgerpractice.ledger;

public class FundTransferSubmissionFailedException extends RuntimeException {

    public FundTransferSubmissionFailedException(Long transferRequestId) {
        super("Fund transfer submission failed for requestId=" + transferRequestId
                + ". Will be retried automatically up to the configured attempt limit; no action needed unless retries are exhausted.");
    }
}
