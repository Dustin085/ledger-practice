package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.outbox.TransferResultMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.example.ledgerpractice.outbox.RabbitConfig.TRANSFER_RESULT_QUEUE_NAME;

@Component
@RequiredArgsConstructor
public class TransferResultListener {

    private final TransferResultService transferResultService;

    @RabbitListener(queues = TRANSFER_RESULT_QUEUE_NAME)
    public void handleTransferResult(TransferResultMessage message) {
        try {
            switch (message.result()) {
                case CONFIRMED -> transferResultService.confirm(message.externalReferenceId(), message.externalEventId());
                case FAILED -> transferResultService.fail(message.externalReferenceId(), message.externalEventId());
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
