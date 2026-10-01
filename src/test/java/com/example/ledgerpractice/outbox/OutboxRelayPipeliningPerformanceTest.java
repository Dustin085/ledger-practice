package com.example.ledgerpractice.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static com.example.ledgerpractice.outbox.RabbitConfig.AUDIT_LOG_QUEUE_NAME;
import static com.example.ledgerpractice.outbox.RabbitConfig.SUBMISSION_QUEUE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

// 用真的 RabbitMQ 連線證明 pipelining 真的省時間：連續量「單筆」當基準，
// 再量一批 N 筆的總耗時，如果真的有省下 (N-1) 次 RTT，批次耗時應該接近單筆基準，
// 不會隨 N 線性增加。需要真的連 broker，標 requires-rabbitmq，預設被排除（見 README）。
// test profile 預設把 outbox.relay.enabled 關掉（其他測試不需要真的 relay 動起來），
// 這支測試需要真正呼叫 relay()，用 @TestPropertySource 把它蓋回 true，不影響其他測試。
// @DirtiesContext：這個 context 的 @Scheduled relay() 是真的活著的，Spring 測試框架預設
// 會快取 context 給下一個測試類別重複使用，如果不標這個，它會在背景繼續跑，撈到其他測試
// 留在同一個 H2 記憶體資料庫裡的 OutboxEvent 去搶著發布，干擾其他測試（實測真的會）。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "outbox.relay.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("requires-rabbitmq")
class OutboxRelayPipeliningPerformanceTest {

    private static final String PAYLOAD_JSON =
            "{\"transferRequestId\":1,\"journalEntryId\":2,\"amount\":10,\"externalCounterparty\":\"x\"}";
    private static final int BATCH_SIZE = 20;

    @Autowired
    private OutboxRelay outboxRelay;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    @AfterEach
    void purgeQueues() {
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(SUBMISSION_QUEUE_NAME);
            channel.queuePurge(AUDIT_LOG_QUEUE_NAME);
            return null;
        });
    }

    private OutboxEvent unpublishedEvent() {
        return outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("FundTransferRequest")
                .aggregateId(1L)
                .eventType("FundTransferRequested")
                .payload(PAYLOAD_JSON)
                .build());
    }

    private long timeRelayMillis() {
        long start = System.nanoTime();
        outboxRelay.relay();
        return (System.nanoTime() - start) / 1_000_000;
    }

    @Test
    void relayingABatchIsNotLinearlySlowerThanRelayingOneEvent() {
        unpublishedEvent();
        long singleEventMillis = timeRelayMillis();

        for (int i = 0; i < BATCH_SIZE; i++) {
            unpublishedEvent();
        }
        long batchMillis = timeRelayMillis();

        // countByPublishedAtIsNull 沒有鎖，不需要交易；findByPublishedAtIsNullOrderByIdAsc
        // 現在帶 PESSIMISTIC_WRITE，直接在測試方法裡呼叫會因為沒有交易而噴錯。
        assertThat(outboxEventRepository.countByPublishedAtIsNull()).isZero();

        System.out.println("single event relay() = " + singleEventMillis + "ms, "
                + BATCH_SIZE + "-event batch relay() = " + batchMillis + "ms");

        // 序列版本（送一筆等一筆）理論上會隨 N 線性增加：BATCH_SIZE 筆大約要
        // BATCH_SIZE × singleEventMillis。只要批次耗時遠低於這個線性預期，
        // 就證明真的是整批送出去、同時等結果，不是逐筆等待。給寬裕的安全係數（除以 3）
        // 避免本機偶發的些微延遲讓測試不穩定。
        long linearExpectationMillis = (long) singleEventMillis * BATCH_SIZE;
        assertThat(batchMillis)
                .as("batch of %d should be far below the serial-would-have-taken estimate of %dms",
                        BATCH_SIZE, linearExpectationMillis)
                .isLessThan(Math.max(linearExpectationMillis / 3, singleEventMillis + 50));
    }
}
