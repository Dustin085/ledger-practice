package com.example.ledgerpractice.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitConfigTest {

    // 參數型別是 Message（而非具體 payload 型別）的 listener，轉換器沒有型別可推，只能讀 __TypeId__ 標頭，
    // 這時 trusted packages 的設定就會生效：訊息類別所在的套件（outbox）必須被列為受信任。
    @Test
    void converterTrustsTheOutboxPayloadClasses() {
        MessageConverter converter = new RabbitConfig().jsonMessageConverter();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", FundTransferRequestedPayload.class.getName());
        Message message = new Message(
                "{\"transferRequestId\":42,\"journalEntryId\":7,\"amount\":100.00,\"externalCounterparty\":\"X\"}"
                        .getBytes(StandardCharsets.UTF_8),
                properties);

        Object converted = converter.fromMessage(message);

        assertThat(converted).isInstanceOf(FundTransferRequestedPayload.class);
        assertThat(((FundTransferRequestedPayload) converted).transferRequestId()).isEqualTo(42L);
    }
}
