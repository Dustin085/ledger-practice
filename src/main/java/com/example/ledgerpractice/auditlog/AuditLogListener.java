package com.example.ledgerpractice.auditlog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.example.ledgerpractice.outbox.RabbitConfig.AUDIT_LOG_QUEUE_NAME;

// audit queue 以 "#" 綁在 outbox exchange 上，會收到所有 aggregate 的事件；routing key 的格式是
// "<aggregateType>.<eventType>"。新增 aggregate 種類時，只要在 AGGREGATE_ID_FIELDS 補一筆。
// 刻意不加 @Transactional：save 撞到唯一約束時，交易一旦被標記 rollback-only，這裡就接不住了。
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogListener {

    private static final Map<String, String> AGGREGATE_ID_FIELDS = Map.of(
            "FundTransferRequest", "transferRequestId");

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = AUDIT_LOG_QUEUE_NAME)
    public void record(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        int separator = routingKey == null ? -1 : routingKey.indexOf('.');
        if (separator < 0) {
            log.error("Audit log 略過：routing key 格式不是 <aggregateType>.<eventType>，routingKey={}", routingKey);
            return;
        }
        String aggregateType = routingKey.substring(0, separator);
        String eventType = routingKey.substring(separator + 1);

        String idField = AGGREGATE_ID_FIELDS.get(aggregateType);
        if (idField == null) {
            log.error("Audit log 略過：不認得的 aggregateType={}，請在 AGGREGATE_ID_FIELDS 補上", aggregateType);
            return;
        }

        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        JsonNode idNode = objectMapper.readTree(payload).get(idField);
        if (idNode == null || idNode.isNull()) {
            log.error("Audit log 略過：payload 缺少 {} 欄位，aggregateType={}", idField, aggregateType);
            return;
        }

        AuditLog auditLog = AuditLog.builder()
                .aggregateType(aggregateType)
                .aggregateId(idNode.asLong())
                .eventType(eventType)
                .payload(payload)
                .build();
        try {
            auditLogRepository.save(auditLog);
        } catch (DataIntegrityViolationException e) {
            log.info("Audit log 略過（可能已記錄）：{}.{} id={}，原因：{}",
                    aggregateType, eventType, auditLog.getAggregateId(), e.getMostSpecificCause().getMessage());
        }
    }
}
