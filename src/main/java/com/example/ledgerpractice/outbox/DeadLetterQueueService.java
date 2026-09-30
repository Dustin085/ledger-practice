package com.example.ledgerpractice.outbox;

import com.rabbitmq.client.GetResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DeadLetterQueueService {
    private final RabbitTemplate rabbitTemplate;

    public DeadLetterQueueService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public long countMessages(String dlqName) {
        return rabbitTemplate.execute(channel -> channel.messageCount(dlqName));
    }

    public Optional<DeadLetterMessage> peekNext(String dlqName) {
        return rabbitTemplate.execute(channel -> {
            GetResponse response = channel.basicGet(dlqName, false);
            if (response == null) {
                return Optional.empty();
            }
            channel.basicNack(response.getEnvelope().getDeliveryTag(), false, true);
            String body = new String(response.getBody(), StandardCharsets.UTF_8);
            return Optional.of(new DeadLetterMessage(body, response.getProps().getHeaders()));
        });
    }

    public boolean reprocessNext(String dlqName) {
        return rabbitTemplate.execute(channel -> {
            GetResponse response = channel.basicGet(dlqName, false);
            if (response == null) {
                return false;
            }
            long deliveryTag = response.getEnvelope().getDeliveryTag();
            String targetQueue = originalQueueFrom(response.getProps().getHeaders());
            try {
                channel.basicPublish("", targetQueue, response.getProps(), response.getBody());
                channel.basicAck(deliveryTag, false);
                return true;
            } catch (IOException e) {
                channel.basicNack(deliveryTag, false, true);
                throw e;
            }
        });
    }

    public boolean discardNext(String dlqName) {
        return rabbitTemplate.execute(channel -> {
            GetResponse response = channel.basicGet(dlqName, false);
            if (response == null) {
                return false;
            }
            channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
            return true;
        });
    }

    @SuppressWarnings("unchecked")
    private String originalQueueFrom(Map<String, Object> headers) {
        List<Map<String, Object>> xDeath = (List<Map<String, Object>>) headers.get("x-death");
        if (xDeath == null || xDeath.isEmpty()) {
            throw new IllegalStateException("Message has no x-death header; cannot determine original queue");
        }
        return xDeath.getFirst().get("queue").toString();
    }
}