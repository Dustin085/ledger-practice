package com.example.ledgerpractice.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final String PAYLOAD_JSON =
            "{\"transferRequestId\":1,\"journalEntryId\":2,\"amount\":10,\"externalCounterparty\":\"x\"}";
    private static final int BATCH_SIZE = 50;

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxEventRepository, new ObjectMapper(), rabbitTemplate, Duration.ofMillis(100), BATCH_SIZE);
    }

    private OutboxEvent event(long id) {
        return eventWithPayload(id, PAYLOAD_JSON);
    }

    private OutboxEvent eventWithPayload(long id, String payload) {
        return OutboxEvent.builder()
                .id(id)
                .aggregateType("FundTransferRequest")
                .aggregateId(id)
                .eventType("FundTransferRequested")
                .payload(payload)
                .build();
    }

    private void givenUnpublished(OutboxEvent... events) {
        when(outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc(any(Pageable.class))).thenReturn(List.of(events));
    }

    private void brokerAnswers(Answer<Object> answer) {
        doAnswer(answer).when(rabbitTemplate)
                .convertAndSend(eq(RabbitConfig.OUTBOX_EXCHANGE_NAME), anyString(), any(Object.class), any(CorrelationData.class));
    }

    private static void complete(org.mockito.invocation.InvocationOnMock invocation, CorrelationData.Confirm confirm) {
        CorrelationData correlationData = invocation.getArgument(3);
        correlationData.getFuture().complete(confirm);
    }

    private static Answer<Object> ack() {
        return invocation -> {
            complete(invocation, new CorrelationData.Confirm(true, null));
            return null;
        };
    }

    private void verifyPublishAttempts(int times) {
        verify(rabbitTemplate, times(times)).convertAndSend(
                eq(RabbitConfig.OUTBOX_EXCHANGE_NAME), anyString(), any(Object.class), any(CorrelationData.class));
    }

    @Test
    void onlyOneBatchOfOrderedEventsIsRequestedPerRun() {
        givenUnpublished();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        relay.relay();

        verify(outboxEventRepository).findByPublishedAtIsNullOrderByIdAsc(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(BATCH_SIZE);
    }

    @Test
    void ackedEventIsMarkedPublished() {
        OutboxEvent outboxEvent = event(1);
        givenUnpublished(outboxEvent);
        brokerAnswers(ack());

        relay.relay();

        assertThat(outboxEvent.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(outboxEvent);
    }

    @Test
    void nackAbortsAwaitingConfirmsSoNeitherEventEndsUpMarkedPublished() {
        // 兩階段設計下，整批會先送出去（不是送一筆等一筆），所以 second 的 convertAndSend
        // 一定會被呼叫到；但等 confirm 時第一筆就 nack，會直接中止，不會繼續等 second 的結果，
        // 兩筆都不會被標記已發布。
        OutboxEvent first = event(1);
        OutboxEvent second = event(2);
        givenUnpublished(first, second);
        brokerAnswers(invocation -> {
            complete(invocation, new CorrelationData.Confirm(false, "disk full"));
            return null;
        });

        relay.relay();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNull();
        verifyPublishAttempts(2);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void noConfirmWithinTimeoutAbortsAwaitingConfirmsSoRemainingEventsDoNotEachWaitForTheTimeout() {
        // 一樣先送完整批（second 也會被送出），但等 confirm 時第一筆逾時就直接中止，
        // 不會接著對 second 的 future 再等一次 timeout。只看最終狀態（都沒發布）不夠，
        // 因為不論有沒有真的省下第二次等待，最終狀態都一樣是「都沒發布」——用耗時證明
        // 真的只等了一次 timeout（100ms），不是兩次（200ms）。
        OutboxEvent first = event(1);
        OutboxEvent second = event(2);
        givenUnpublished(first, second);
        brokerAnswers(invocation -> null);

        long start = System.nanoTime();
        relay.relay();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNull();
        verifyPublishAttempts(2);
        verify(outboxEventRepository, never()).save(any());
        assertThat(elapsedMillis).as("should only pay one confirmTimeout, not one per event").isLessThan(180);
    }

    @Test
    void connectionFailureAbortsTheRun() {
        OutboxEvent first = event(1);
        OutboxEvent second = event(2);
        givenUnpublished(first, second);
        brokerAnswers(invocation -> {
            throw new AmqpConnectException(new RuntimeException("broker down"));
        });

        relay.relay();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNull();
        verifyPublishAttempts(1);
    }

    @Test
    void unroutableEventIsSkippedButTheRestOfTheBatchStillGoesOut() {
        OutboxEvent first = event(1);
        OutboxEvent second = event(2);
        givenUnpublished(first, second);
        brokerAnswers(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            if ("1".equals(correlationData.getId())) {
                correlationData.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE",
                        RabbitConfig.OUTBOX_EXCHANGE_NAME, "any.key"));
            }
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        });

        relay.relay();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(second);
    }

    @Test
    void eventWithBrokenPayloadIsSkippedButTheRestOfTheBatchStillGoesOut() {
        OutboxEvent poison = eventWithPayload(1, "not json");
        OutboxEvent healthy = event(2);
        givenUnpublished(poison, healthy);
        brokerAnswers(ack());

        relay.relay();

        assertThat(poison.getPublishedAt()).isNull();
        assertThat(healthy.getPublishedAt()).isNotNull();
        verifyPublishAttempts(1);
    }
}
