package com.example.ledgerpractice.auditlog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// 刻意不加 @Transactional：listener 在正式環境沒有外層交易，重複訊息撞唯一約束後要能繼續運作；
// 測試若包在交易裡，行為會跟正式環境不一樣。純 JPA/H2，不需要連 RabbitMQ。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AuditLogListenerTest {

    private static final String ROUTING_KEY = "FundTransferRequest.FundTransferRequested";

    @Autowired
    private AuditLogListener listener;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void cleanUp() {
        auditLogRepository.deleteAll();
    }

    private Message message(String routingKey, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    void recordsAggregateTypeEventTypeAndPayloadFromMessage() {
        String json = "{\"transferRequestId\":42,\"journalEntryId\":7,\"amount\":100.00,\"externalCounterparty\":\"X\"}";

        listener.record(message(ROUTING_KEY, json));

        assertThat(auditLogRepository.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getAggregateType()).isEqualTo("FundTransferRequest");
            assertThat(log.getAggregateId()).isEqualTo(42L);
            assertThat(log.getEventType()).isEqualTo("FundTransferRequested");
            assertThat(log.getPayload()).isEqualTo(json);
        });
    }

    @Test
    void duplicateMessageIsIgnoredAndDoesNotThrow() {
        String json = "{\"transferRequestId\":42}";

        listener.record(message(ROUTING_KEY, json));
        listener.record(message(ROUTING_KEY, json));

        assertThat(auditLogRepository.count()).isEqualTo(1);
    }

    @Test
    void sameAggregateDifferentEventTypeIsRecordedSeparately() {
        String json = "{\"transferRequestId\":42}";

        listener.record(message("FundTransferRequest.FundTransferRequested", json));
        listener.record(message("FundTransferRequest.FundTransferCompleted", json));

        assertThat(auditLogRepository.count()).isEqualTo(2);
    }

    @Test
    void payloadLongerThan255CharsIsStored() {
        String longCounterparty = "A".repeat(600);
        String json = "{\"transferRequestId\":42,\"externalCounterparty\":\"" + longCounterparty + "\"}";

        listener.record(message(ROUTING_KEY, json));

        assertThat(auditLogRepository.findAll()).singleElement()
                .satisfies(log -> assertThat(log.getPayload()).isEqualTo(json));
    }

    @Test
    void unknownAggregateTypeIsSkipped() {
        listener.record(message("Mystery.Happened", "{\"id\":1}"));

        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void routingKeyWithoutSeparatorIsSkipped() {
        listener.record(message("nodot", "{\"transferRequestId\":42}"));

        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void payloadMissingIdFieldIsSkipped() {
        listener.record(message(ROUTING_KEY, "{\"amount\":100}"));

        assertThat(auditLogRepository.count()).isZero();
    }
}
