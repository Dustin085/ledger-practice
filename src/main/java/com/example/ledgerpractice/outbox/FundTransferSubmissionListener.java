package com.example.ledgerpractice.outbox;

import com.example.ledgerpractice.ledger.FundTransferRequestNotFoundException;
import com.example.ledgerpractice.ledger.SubmitOutcome;
import com.example.ledgerpractice.ledger.TransferSubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.example.ledgerpractice.outbox.RabbitConfig.SUBMISSION_QUEUE_NAME;

@Component
@RequiredArgsConstructor
@Slf4j
public class FundTransferSubmissionListener {
    private final TransferSubmissionService transferSubmissionService;

    @RabbitListener(queues = SUBMISSION_QUEUE_NAME)
    public void handleFundTransferSubmission(FundTransferRequestedPayload payload) {
        try {
            SubmitOutcome outcome = transferSubmissionService.submit(payload.transferRequestId());
            if (outcome == SubmitOutcome.FAILED) {
                throw new RuntimeException("轉帳請求失敗， requestId= " + payload.transferRequestId());
            }
            if (outcome == SubmitOutcome.GAVE_UP) {
                log.warn("轉帳請求超過重試上限，已放棄並補償: requestId={}", payload.transferRequestId());
            }
        } catch (FundTransferRequestNotFoundException e) {
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
