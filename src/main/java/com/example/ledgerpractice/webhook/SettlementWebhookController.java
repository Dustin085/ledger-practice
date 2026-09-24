package com.example.ledgerpractice.webhook;

import com.example.ledgerpractice.outbox.TransferResult;
import com.example.ledgerpractice.outbox.TransferResultMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import static com.example.ledgerpractice.outbox.RabbitConfig.EXTERNAL_SETTLEMENT_EXCHANGE_NAME;
import static com.example.ledgerpractice.outbox.RabbitConfig.TRANSFER_CONFIRMED_ROUTING_KEY;
import static com.example.ledgerpractice.outbox.RabbitConfig.TRANSFER_FAILED_ROUTING_KEY;

// 外部金流系統的 callback 入口。只負責「驗證來源 + 接手排隊」，不做任何帳務處理：
// 真正的 confirm/fail 由 TransferResultListener 非同步執行。
// 發布訊息成功才回 200；如果 broker 連不上，例外會往外拋成 5xx，外部系統看到失敗就會重送。
@RestController
@RequestMapping("/webhooks/settlement")
@RequiredArgsConstructor
public class SettlementWebhookController {

    private final WebhookSigner webhookSigner;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    // 要用原始字串驗簽章：簽章是對送出的原始 body 算的，先解析成物件再重新序列化，內容可能已經不同。
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(value = WebhookSigner.SIGNATURE_HEADER, required = false) String signature) {

        if (!webhookSigner.verify(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        TransferResultMessage message;
        try {
            message = objectMapper.readValue(rawBody, TransferResultMessage.class);
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().build();
        }
        if (message.externalReferenceId() == null || message.externalEventId() == null || message.result() == null) {
            return ResponseEntity.badRequest().build();
        }

        String routingKey = message.result() == TransferResult.CONFIRMED
                ? TRANSFER_CONFIRMED_ROUTING_KEY
                : TRANSFER_FAILED_ROUTING_KEY;
        rabbitTemplate.convertAndSend(EXTERNAL_SETTLEMENT_EXCHANGE_NAME, routingKey, message);
        return ResponseEntity.ok().build();
    }
}
