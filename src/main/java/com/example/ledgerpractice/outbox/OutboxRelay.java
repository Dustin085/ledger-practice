package com.example.ledgerpractice.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.example.ledgerpractice.outbox.RabbitConfig.OUTBOX_EXCHANGE_NAME;

@Component
@Slf4j
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;
    private final int batchSize;

    public OutboxRelay(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            @Value("${outbox.relay.confirm-timeout:5s}") Duration confirmTimeout,
            @Value("${outbox.relay.batch-size:100}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<OutboxEvent> outboxEvents =
                outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, batchSize));
        for (OutboxEvent outboxEvent : outboxEvents) {
            try {
                if (publishAndAwaitConfirm(outboxEvent)) {
                    outboxEvent.setPublishedAt(Instant.now());
                    outboxEventRepository.save(outboxEvent);
                }
            } catch (BrokerUnavailableException e) {
                // broker 明顯有問題（連不上、不回應、nack）時，後面每一筆多半也會一樣，
                // 逐筆各等一次逾時只是把這一輪（以及共用排程執行緒的其他任務）拖得更久。
                // 直接放棄這一輪，剩下的事件都還沒標記，下一輪會重來。
                log.warn("broker 目前無法確認訊息，中止本輪發布，剩餘事件留待下一輪，outboxEventId={}",
                        outboxEvent.getId(), e);
                break;
            } catch (RuntimeException e) {
                // 只跟這一筆有關的問題（例如 payload 壞掉、被退回）不該卡住其他事件。
                log.warn("Outbox 事件發布失敗，稍後重送，outboxEventId={}", outboxEvent.getId(), e);
            }
        }
    }

    // convertAndSend 回來只代表「已交給客戶端送出」，不代表 broker 收到。
    // 必須等到 broker 的 ack 才能標記已發布，否則訊息半路遺失時，Outbox 卻以為送出了，永遠不會重送。
    // 回傳 false 代表這一筆「不確定 broker 有收到」，不標記，留給下一輪重送（at-least-once，
    // 重複的訊息由消費端冪等處理）。broker 整體有問題時丟 BrokerUnavailableException。
    private boolean publishAndAwaitConfirm(OutboxEvent outboxEvent) {
        FundTransferRequestedPayload payload =
                objectMapper.readValue(outboxEvent.getPayload(), FundTransferRequestedPayload.class);
        CorrelationData correlationData = new CorrelationData(String.valueOf(outboxEvent.getId()));
        try {
            rabbitTemplate.convertAndSend(
                    OUTBOX_EXCHANGE_NAME,
                    outboxEvent.getAggregateType() + "." + outboxEvent.getEventType(),
                    payload,
                    correlationData);
        } catch (AmqpException e) {
            throw new BrokerUnavailableException(e);
        }

        CorrelationData.Confirm confirm;
        try {
            confirm = correlationData.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            throw new BrokerUnavailableException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrokerUnavailableException(e);
        }

        if (!confirm.isAck()) {
            throw new BrokerUnavailableException(new IllegalStateException("broker nack: " + confirm.getReason()));
        }
        // exchange 收到就會回 ack，就算沒有任何 queue 接得到；被 mandatory 退回的訊息會在 ack 之前
        // 先設定 returned，所以要另外檢查，否則「送進黑洞」也會被當成成功。
        if (correlationData.getReturned() != null) {
            log.warn("訊息沒有任何 queue 接收而被退回，outboxEventId={}，routingKey={}",
                    outboxEvent.getId(), correlationData.getReturned().getRoutingKey());
            return false;
        }
        return true;
    }

    private static class BrokerUnavailableException extends RuntimeException {
        BrokerUnavailableException(Throwable cause) {
            super(cause);
        }
    }
}
