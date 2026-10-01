package com.example.ledgerpractice.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

    @Async("outboxAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOutboxEventCreated(OutboxEventCreatedEvent event) {
        // findByIdAndPublishedAtIsNull 用 SKIP LOCKED 鎖列：如果排程那邊剛好也在處理
        // 同一筆，這裡會直接查不到（不是卡住等待），什麼都不做，留給排程處理就好。
        outboxEventRepository.findByIdAndPublishedAtIsNull(event.outboxEventId())
                .ifPresent(this::publishSingle);
    }

    private void publishSingle(OutboxEvent outboxEvent) {
        try {
            CorrelationData correlationData = publish(outboxEvent);
            if (awaitConfirm(outboxEvent, correlationData)) {
                outboxEvent.setPublishedAt(Instant.now());
                outboxEventRepository.save(outboxEvent);
            }
        } catch (RuntimeException e) {
            log.warn("立即發布失敗，留給排程兜底，outboxEventId={}", outboxEvent.getId(), e);
        }
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:30000}")
    @Transactional
    public void relay() {
        // 鎖要撐到這整輪發布完才能釋放，交易邊界故意跟著整個方法走，不只包住查詢本身；
        // 代價是這段期間會佔用一條 DB connection，包含等 RabbitMQ confirm 的時間。
        List<OutboxEvent> outboxEvents =
                outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, batchSize));

        // 第一階段：全部送出、不等待。等 confirm 本質上是在等一次網路來回（RTT），
        // 先把整批都送出去，broker 收到之後大致會接近同時回 ack，比逐筆「送一筆等一筆」
        // 省下 (N-1) 次 RTT 的等待時間；也間接縮短上面那個交易持有鎖的時間。
        List<PendingPublish> pending = new ArrayList<>();
        for (OutboxEvent outboxEvent : outboxEvents) {
            try {
                pending.add(new PendingPublish(outboxEvent, publish(outboxEvent)));
            } catch (BrokerUnavailableException e) {
                // broker 連線層級就出問題（送都送不出去），後面大概率也一樣，直接放棄這一輪。
                log.warn("broker 目前無法送出訊息，中止本輪發布，剩餘事件留待下一輪，outboxEventId={}",
                        outboxEvent.getId(), e);
                break;
            } catch (RuntimeException e) {
                // 只跟這一筆有關的問題（例如 payload 壞掉）不該卡住其他事件。
                log.warn("Outbox 事件發布失敗，稍後重送，outboxEventId={}", outboxEvent.getId(), e);
            }
        }

        // 第二階段：逐筆等 confirm。broker 真的掛掉時，第一筆等到逾時就會 break，
        // 不會因為拆成兩階段反而變成每筆各付一次逾時成本。
        for (PendingPublish p : pending) {
            try {
                if (awaitConfirm(p.outboxEvent(), p.correlationData())) {
                    p.outboxEvent().setPublishedAt(Instant.now());
                    outboxEventRepository.save(p.outboxEvent());
                }
            } catch (BrokerUnavailableException e) {
                log.warn("broker 目前無法確認訊息，中止本輪等待確認，剩餘事件留待下一輪，outboxEventId={}",
                        p.outboxEvent().getId(), e);
                break;
            } catch (RuntimeException e) {
                log.warn("Outbox 事件發布失敗，稍後重送，outboxEventId={}", p.outboxEvent().getId(), e);
            }
        }
    }

    private record PendingPublish(OutboxEvent outboxEvent, CorrelationData correlationData) {
    }

    // convertAndSend 回來只代表「已交給客戶端送出」，不代表 broker 收到；真正的確認在 awaitConfirm。
    private CorrelationData publish(OutboxEvent outboxEvent) {
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
        return correlationData;
    }

    // 必須等到 broker 的 ack 才能標記已發布，否則訊息半路遺失時，Outbox 卻以為送出了，永遠不會重送。
    // 回傳 false 代表這一筆「不確定 broker 有收到」，不標記，留給下一輪重送（at-least-once，
    // 重複的訊息由消費端冪等處理）。broker 整體有問題時丟 BrokerUnavailableException。
    private boolean awaitConfirm(OutboxEvent outboxEvent, CorrelationData correlationData) {
        CorrelationData.Confirm confirm;
        try {
            confirm = correlationData.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            throw new BrokerUnavailableException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrokerUnavailableException(e);
        }

        if (!confirm.ack()) {
            throw new BrokerUnavailableException(new IllegalStateException("broker nack: " + confirm.reason()));
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
