package com.example.ledgerpractice.outbox;

import com.rabbitmq.client.AMQP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.ledgerpractice.outbox.RabbitConfig.SUBMISSION_DEAD_LETTER_QUEUE_NAME;
import static com.example.ledgerpractice.outbox.RabbitConfig.SUBMISSION_QUEUE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

// 需要真的連上 RabbitMQ 才能跑（直接操作 basicGet/basicAck/basicNack），
// 不能像其他測試一樣靠 test profile 關掉 listener 就跳過對 broker 的依賴。
// 標成 requires-rabbitmq，預設被 pom.xml 的 surefire 設定排除，見 README「測試」章節。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Tag("requires-rabbitmq")
class DeadLetterQueueServiceTest {

    @Autowired
    private DeadLetterQueueService deadLetterQueueService;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    @AfterEach
    void purgeQueues() {
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(SUBMISSION_DEAD_LETTER_QUEUE_NAME);
            channel.queuePurge(SUBMISSION_QUEUE_NAME);
            return null;
        });
    }

    // 直接把訊息連同一個手刻的 x-death 標頭塞進 DLQ，模擬「這筆訊息真的是從 originalQueue
    // 被 DLX 轉送過來的」，不用真的跑一次完整的重試耗盡流程。
    private void publishDeadLetter(String body, String originalQueue) {
        rabbitTemplate.execute(channel -> {
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .headers(Map.of("x-death", List.of(
                            Map.of("queue", originalQueue, "reason", "rejected", "count", 1L))))
                    .build();
            channel.basicPublish("", SUBMISSION_DEAD_LETTER_QUEUE_NAME, props,
                    body.getBytes(StandardCharsets.UTF_8));
            return null;
        });
    }

    @Test
    void countMessagesIsZeroWhenQueueEmpty() {
        assertThat(deadLetterQueueService.countMessages(SUBMISSION_DEAD_LETTER_QUEUE_NAME)).isZero();
    }

    @Test
    void peekNextReturnsEmptyWhenQueueEmpty() {
        assertThat(deadLetterQueueService.peekNext(SUBMISSION_DEAD_LETTER_QUEUE_NAME)).isEmpty();
    }

    @Test
    void peekNextReturnsMessageWithoutRemovingIt() {
        publishDeadLetter("{\"hello\":\"world\"}", SUBMISSION_QUEUE_NAME);

        Optional<DeadLetterMessage> first = deadLetterQueueService.peekNext(SUBMISSION_DEAD_LETTER_QUEUE_NAME);
        Optional<DeadLetterMessage> second = deadLetterQueueService.peekNext(SUBMISSION_DEAD_LETTER_QUEUE_NAME);

        assertThat(first).isPresent();
        assertThat(first.get().body()).isEqualTo("{\"hello\":\"world\"}");
        assertThat(second).isPresent();
        assertThat(deadLetterQueueService.countMessages(SUBMISSION_DEAD_LETTER_QUEUE_NAME)).isEqualTo(1);
    }

    @Test
    void reprocessNextRepublishesToQueueRecordedInXDeathAndRemovesFromDlq() {
        publishDeadLetter("{\"payload\":\"retry-me\"}", SUBMISSION_QUEUE_NAME);

        boolean processed = deadLetterQueueService.reprocessNext(SUBMISSION_DEAD_LETTER_QUEUE_NAME);

        assertThat(processed).isTrue();
        assertThat(deadLetterQueueService.countMessages(SUBMISSION_DEAD_LETTER_QUEUE_NAME)).isZero();
        Message redelivered = rabbitTemplate.receive(SUBMISSION_QUEUE_NAME, 5000);
        assertThat(redelivered).isNotNull();
        assertThat(new String(redelivered.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"payload\":\"retry-me\"}");
    }

    @Test
    void reprocessNextReturnsFalseWhenQueueEmpty() {
        assertThat(deadLetterQueueService.reprocessNext(SUBMISSION_DEAD_LETTER_QUEUE_NAME)).isFalse();
    }

    @Test
    void discardNextRemovesMessageWithoutRepublishing() {
        publishDeadLetter("{\"payload\":\"throwaway\"}", SUBMISSION_QUEUE_NAME);

        boolean discarded = deadLetterQueueService.discardNext(SUBMISSION_DEAD_LETTER_QUEUE_NAME);

        assertThat(discarded).isTrue();
        assertThat(deadLetterQueueService.countMessages(SUBMISSION_DEAD_LETTER_QUEUE_NAME)).isZero();
        assertThat(rabbitTemplate.receive(SUBMISSION_QUEUE_NAME, 500)).isNull();
    }

    @Test
    void discardNextReturnsFalseWhenQueueEmpty() {
        assertThat(deadLetterQueueService.discardNext(SUBMISSION_DEAD_LETTER_QUEUE_NAME)).isFalse();
    }
}
