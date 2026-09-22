package com.example.ledgerpractice.outbox;

import com.example.ledgerpractice.ledger.SubmitOutcome;
import com.example.ledgerpractice.ledger.TransferSubmissionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxRelay {
    private final OutboxEventRepository outboxEventRepository;
    private final TransferSubmissionService transferSubmissionService;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, TransferSubmissionService transferSubmissionService) {
        this.outboxEventRepository = outboxEventRepository;
        this.transferSubmissionService = transferSubmissionService;
    }

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByPublishedAtIsNull();
        for (OutboxEvent outboxEvent : outboxEvents) {
            SubmitOutcome submitOutcome = transferSubmissionService.submit(outboxEvent.getAggregateId());
            if (submitOutcome == SubmitOutcome.SUBMITTED
                    || submitOutcome == SubmitOutcome.ALREADY_HANDLED
                    || submitOutcome == SubmitOutcome.GAVE_UP) {
                outboxEvent.setPublishedAt(Instant.now());
                outboxEventRepository.save(outboxEvent);
            }
        }
    }
}
