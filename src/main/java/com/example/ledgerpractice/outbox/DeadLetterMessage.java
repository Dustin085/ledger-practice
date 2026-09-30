package com.example.ledgerpractice.outbox;

import java.util.Map;

public record DeadLetterMessage(String body, Map<String, Object> headers) {}
