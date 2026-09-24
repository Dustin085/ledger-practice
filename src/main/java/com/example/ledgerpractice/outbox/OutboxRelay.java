package com.example.ledgerpractice.outbox;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static com.example.ledgerpractice.outbox.RabbitConfig.EXCHANGE_NAME;

@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper, RabbitTemplate rabbitTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByPublishedAtIsNull();
        for (OutboxEvent outboxEvent : outboxEvents) {
            FundTransferRequestedPayload payload = objectMapper.readValue(outboxEvent.getPayload(), FundTransferRequestedPayload.class);
            rabbitTemplate.convertAndSend(
                    EXCHANGE_NAME,
                    outboxEvent.getAggregateType() + "." + outboxEvent.getEventType(),
                    payload
            );
            outboxEvent.setPublishedAt(Instant.now());
            outboxEventRepository.save(outboxEvent);
        }
    }
}
