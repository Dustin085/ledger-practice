package com.example.ledgerpractice.metrics;

import com.example.ledgerpractice.outbox.OutboxEvent;
import com.example.ledgerpractice.outbox.OutboxEventRepository;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.TransferStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class LedgerMetricsTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private FundTransferRequestRepository fundTransferRequestRepository;

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        new LedgerMetrics(registry, outboxEventRepository, fundTransferRequestRepository);
    }

    @Test
    void unpublishedGaugeReflectsTheCurrentBacklog() {
        lenient().when(outboxEventRepository.countByPublishedAtIsNull()).thenReturn(42L);

        assertThat(registry.get("ledger.outbox.unpublished").gauge().value()).isEqualTo(42.0);
    }

    @Test
    void oldestUnpublishedAgeIsTheAgeOfTheFirstUnpublishedEvent() {
        OutboxEvent oldest = OutboxEvent.builder().createdAt(Instant.now().minusSeconds(30)).build();
        lenient().when(outboxEventRepository.findFirstByPublishedAtIsNullOrderByIdAsc()).thenReturn(Optional.of(oldest));

        assertThat(registry.get("ledger.outbox.oldest.unpublished.age.seconds").gauge().value())
                .isCloseTo(30.0, within(2.0));
    }

    @Test
    void oldestUnpublishedAgeIsZeroWhenNothingIsWaiting() {
        lenient().when(outboxEventRepository.findFirstByPublishedAtIsNullOrderByIdAsc()).thenReturn(Optional.empty());

        assertThat(registry.get("ledger.outbox.oldest.unpublished.age.seconds").gauge().value()).isZero();
    }

    @Test
    void transferGaugesAreTaggedByStatus() {
        lenient().when(fundTransferRequestRepository.countByStatus(TransferStatus.CREATED)).thenReturn(3L);
        lenient().when(fundTransferRequestRepository.countByStatus(TransferStatus.SUBMITTED)).thenReturn(7L);

        assertThat(registry.get("ledger.transfers").tag("status", "CREATED").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("ledger.transfers").tag("status", "SUBMITTED").gauge().value()).isEqualTo(7.0);
    }
}
