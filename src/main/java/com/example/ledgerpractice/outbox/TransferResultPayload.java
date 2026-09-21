package com.example.ledgerpractice.outbox;

public record TransferResultPayload(Long fundTransferRequestId, String outcome) {
}
