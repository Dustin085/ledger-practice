package com.example.ledgerpractice.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 驗證 @Lock(PESSIMISTIC_WRITE) + jakarta.persistence.lock.timeout=-2 這個 Hibernate 寫法，
// 是否真的在 H2 上產生正確的 FOR UPDATE SKIP LOCKED 行為（不是只有語法不報錯，而是真的會跳過
// 被別的交易鎖住的那一列）。這支測試不需要連 RabbitMQ，純粹測 JPA/Hibernate/H2 這一層。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class OutboxEventRepositorySkipLockedTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void findByPublishedAtIsNullOrderByIdAscSkipsRowLockedByAnotherTransaction() throws Exception {
        OutboxEvent event1 = outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("Test").aggregateId(1L).eventType("Test").payload("{}").build());
        OutboxEvent event2 = outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("Test").aggregateId(2L).eventType("Test").payload("{}").build());

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();

        TransactionTemplate holderTemplate = new TransactionTemplate(transactionManager);
        holderTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Future<?> holderFuture = pool.submit(() -> holderTemplate.executeWithoutResult(status -> {
            outboxEventRepository.findByIdAndPublishedAtIsNull(event1.getId());
            locked.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        assertThat(locked.await(5, TimeUnit.SECONDS)).as("holder thread acquired the lock").isTrue();

        TransactionTemplate readerTemplate = new TransactionTemplate(transactionManager);
        readerTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<OutboxEvent> visibleToOther = readerTemplate.execute(status ->
                outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10)));

        release.countDown();
        holderFuture.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(visibleToOther).extracting(OutboxEvent::getId)
                .as("locked event1 must be skipped, unrelated unpublished rows (incl. seed data) still visible")
                .doesNotContain(event1.getId())
                .contains(event2.getId());
    }
}
