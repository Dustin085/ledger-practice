package com.example.ledgerpractice.auditlog;

import com.example.ledgerpractice.outbox.FundTransferRequestedPayload;
import com.example.ledgerpractice.outbox.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.example.ledgerpractice.outbox.RabbitConfig.AUDIT_LOG_QUEUE_NAME;

@Component
@Slf4j
public class AuditLogListener {

    @RabbitListener(queues = AUDIT_LOG_QUEUE_NAME)
    public void handleFundTransferSubmissionAuditLog(FundTransferRequestedPayload payload) {
        log.info("收到轉帳請求: requestId={}", payload.transferRequestId());
    }
}
