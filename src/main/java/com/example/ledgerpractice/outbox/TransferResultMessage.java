package com.example.ledgerpractice.outbox;

public record TransferResultMessage(
        String externalReferenceId,
        String externalEventId,
        TransferResult result) {
}
