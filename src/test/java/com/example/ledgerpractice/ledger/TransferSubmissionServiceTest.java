package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.payment.ExternalPaymentGateway;
import com.example.ledgerpractice.payment.PaymentGatewaySubmissionException;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferSubmissionServiceTest {
    @Mock
    private ExternalPaymentGateway externalPaymentGateway;
    @Mock
    private FundTransferRequestRepository fundTransferRequestRepository;
    @Mock
    private CompensationService compensationService;

    private TransferSubmissionService transferSubmissionService;

    @BeforeEach
    void setUp() {
        transferSubmissionService = new TransferSubmissionService(
                externalPaymentGateway,
                fundTransferRequestRepository,
                compensationService);
    }

    @Test
    void alreadyHandledShouldReturnRightOutcome() {
        FundTransferRequest fundTransferRequest = FundTransferRequest.builder()
                .id(1L)
                .status(TransferStatus.SUBMITTED)
                .build();
        when(fundTransferRequestRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(fundTransferRequest));
        SubmitOutcome outcome = transferSubmissionService.submit(1L);
        assertEquals(SubmitOutcome.ALREADY_HANDLED, outcome);
    }

    @Test
    void submittedShouldReturnRightOutcome() {
        FundTransferRequest fundTransferRequest = FundTransferRequest.builder()
                .id(1L)
                .status(TransferStatus.CREATED)
                .build();
        when(fundTransferRequestRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(fundTransferRequest));
        when(externalPaymentGateway.submit(Mockito.any()))
                .thenReturn("MOCK-" + UUID.randomUUID());
        SubmitOutcome outcome = transferSubmissionService.submit(1L);
        assertEquals(SubmitOutcome.SUBMITTED, outcome);
    }

    @Test
    void shouldGaveUpAtMaxAttempts() {
        Integer MAX_ATTEMPTS = (Integer) ReflectionTestUtils.getField(transferSubmissionService, "MAX_ATTEMPTS");
        if (MAX_ATTEMPTS == null) {
            fail("MAX_ATTEMPTS 應該要被設定");
        }
        FundTransferRequest fundTransferRequest = FundTransferRequest.builder()
                .id(1L)
                .status(TransferStatus.CREATED)
                .attemptCount(MAX_ATTEMPTS - 1)
                .build();
        when(fundTransferRequestRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(fundTransferRequest));
        when(externalPaymentGateway.submit(fundTransferRequest))
                .thenThrow(new PaymentGatewaySubmissionException("Payment gateway 拒絕發送"));
        SubmitOutcome outcome = transferSubmissionService.submit(1L);
        assertEquals(SubmitOutcome.GAVE_UP, outcome);
        verify(compensationService).compensate(fundTransferRequest);
    }

    @Test
    void shouldFailedWhenPaymentGatewayIsNotSubmitted() {
        FundTransferRequest fundTransferRequest = FundTransferRequest.builder()
                .id(1L)
                .status(TransferStatus.CREATED)
                .build();
        when(fundTransferRequestRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(fundTransferRequest));
        when(externalPaymentGateway.submit(fundTransferRequest))
                .thenThrow(new PaymentGatewaySubmissionException("Payment gateway 拒絕發送"));
        SubmitOutcome outcome = transferSubmissionService.submit(1L);
        assertEquals(SubmitOutcome.FAILED, outcome);
        verifyNoInteractions(compensationService);

        // 檢查 attempt 相關欄位是否有正確修改
        assertEquals(1, fundTransferRequest.getAttemptCount());
        assertThat(fundTransferRequest.getLastAttemptAt())
                .isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
    }
}