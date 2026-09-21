package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.payment.ExternalPaymentGateway;
import com.example.ledgerpractice.payment.PaymentGatewaySubmissionException;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.TransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TransferSubmissionService {
    private final ExternalPaymentGateway externalPaymentGateway;
    private final FundTransferRequestRepository fundTransferRequestRepository;

    TransferSubmissionService(
            ExternalPaymentGateway externalPaymentGateway,
            FundTransferRequestRepository fundTransferRequestRepository
    ) {
        this.externalPaymentGateway = externalPaymentGateway;
        this.fundTransferRequestRepository = fundTransferRequestRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubmitOutcome submit(Long fundTransferRequestId) {
        FundTransferRequest request = fundTransferRequestRepository.findByIdForUpdate(fundTransferRequestId)
                .orElseThrow(() -> new RuntimeException("Fund Transfer Request Not Found"));
        if (request.getStatus() != TransferStatus.CREATED) {
            return SubmitOutcome.ALREADY_HANDLED;
        }
        request.setAttemptCount(request.getAttemptCount() + 1);
        request.setLastAttemptAt(Instant.now());
        try {
            String externalReferenceId = externalPaymentGateway.submit(request);
            request.setExternalReferenceId(externalReferenceId);
            request.setStatus(TransferStatus.SUBMITTED);

        } catch (PaymentGatewaySubmissionException e) {
            return SubmitOutcome.FAILED;
        }

        return SubmitOutcome.SUBMITTED;
    }
}
