package com.example.ledgerpractice.metrics;

import com.example.ledgerpractice.outbox.OutboxEvent;
import com.example.ledgerpractice.outbox.OutboxEventRepository;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.TransferStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

// 壓測時 JMeter 只看得到請求端的回應時間，看不到「事件積壓」：使用者請求不等 Outbox，
// 請求再快，OutboxRelay 消化不完，積壓也會一直長大。這幾個 gauge 讓積壓與延遲看得見。
// Gauge 是每次被查詢時才即時計算，所以是「當下的值」，不是累計值。
@Component
public class LedgerMetrics {

    public LedgerMetrics(
            MeterRegistry registry,
            OutboxEventRepository outboxEventRepository,
            FundTransferRequestRepository fundTransferRequestRepository) {

        Gauge.builder("ledger.outbox.unpublished", outboxEventRepository, OutboxEventRepository::countByPublishedAtIsNull)
                .description("尚未發布到 MQ 的 Outbox 事件數（積壓）")
                .register(registry);

        Gauge.builder("ledger.outbox.oldest.unpublished.age.seconds", outboxEventRepository, LedgerMetrics::oldestUnpublishedAgeSeconds)
                .description("最舊一筆未發布事件已經等了幾秒（發布延遲）")
                .baseUnit("seconds")
                .register(registry);

        for (TransferStatus status : new TransferStatus[]{TransferStatus.CREATED, TransferStatus.SUBMITTED}) {
            Gauge.builder("ledger.transfers", fundTransferRequestRepository, repository -> repository.countByStatus(status))
                    .tag("status", status.name())
                    .description("各狀態的轉帳數：CREATED 是還沒送出，SUBMITTED 是等外部回覆")
                    .register(registry);
        }
    }

    private static double oldestUnpublishedAgeSeconds(OutboxEventRepository repository) {
        return repository.findFirstByPublishedAtIsNullOrderByIdAsc()
                .map(OutboxEvent::getCreatedAt)
                .map(createdAt -> Duration.between(createdAt, Instant.now()).toMillis() / 1000.0)
                .orElse(0.0);
    }
}
