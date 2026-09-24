package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.payment.ExternalPaymentGateway;
import com.example.ledgerpractice.payment.ExternalTransferStatus;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferReconciliationServiceTest {

    @Mock
    private FundTransferRequestRepository fundTransferRequestRepository;
    @Mock
    private ExternalPaymentGateway externalPaymentGateway;
    @Mock
    private TransferResultService transferResultService;

    private TransferReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new TransferReconciliationService(
                fundTransferRequestRepository, externalPaymentGateway, transferResultService, Duration.ofMinutes(10));
    }

    private FundTransferRequest stale(String ref) {
        return FundTransferRequest.builder().externalReferenceId(ref).status(TransferStatus.SUBMITTED).build();
    }

    private void givenStale(FundTransferRequest... requests) {
        when(fundTransferRequestRepository.findByStatusAndLastAttemptAtBefore(eq(TransferStatus.SUBMITTED), any(Instant.class)))
                .thenReturn(List.of(requests));
    }

    @Test
    void externalConfirmedTriggersConfirmWithDeterministicEventId() {
        givenStale(stale("REF-1"));
        when(externalPaymentGateway.queryStatus("REF-1")).thenReturn(ExternalTransferStatus.CONFIRMED);

        service.reconcileStaleTransfers();

        verify(transferResultService).confirm("REF-1", "reconcile-REF-1");
        verify(transferResultService, never()).fail(anyString(), anyString());
    }

    @Test
    void externalFailedTriggersFail() {
        givenStale(stale("REF-2"));
        when(externalPaymentGateway.queryStatus("REF-2")).thenReturn(ExternalTransferStatus.FAILED);

        service.reconcileStaleTransfers();

        verify(transferResultService).fail("REF-2", "reconcile-REF-2");
        verify(transferResultService, never()).confirm(anyString(), anyString());
    }

    @Test
    void externalPendingDoesNothing() {
        givenStale(stale("REF-3"));
        when(externalPaymentGateway.queryStatus("REF-3")).thenReturn(ExternalTransferStatus.PENDING);

        service.reconcileStaleTransfers();

        verifyNoInteractions(transferResultService);
    }

    @Test
    void externalNotFoundNeverCompensatesAutomatically() {
        givenStale(stale("REF-4"));
        when(externalPaymentGateway.queryStatus("REF-4")).thenReturn(ExternalTransferStatus.NOT_FOUND);

        service.reconcileStaleTransfers();

        verifyNoInteractions(transferResultService);
    }

    @Test
    void oneFailureDoesNotStopTheRestOfTheBatch() {
        givenStale(stale("REF-5"), stale("REF-6"));
        when(externalPaymentGateway.queryStatus("REF-5")).thenThrow(new RuntimeException("gateway down"));
        when(externalPaymentGateway.queryStatus("REF-6")).thenReturn(ExternalTransferStatus.CONFIRMED);

        service.reconcileStaleTransfers();

        verify(transferResultService).confirm("REF-6", "reconcile-REF-6");
    }

    @Test
    void failureInsideConfirmDoesNotStopTheRestOfTheBatch() {
        givenStale(stale("REF-7"), stale("REF-8"));
        when(externalPaymentGateway.queryStatus("REF-7")).thenReturn(ExternalTransferStatus.CONFIRMED);
        when(externalPaymentGateway.queryStatus("REF-8")).thenReturn(ExternalTransferStatus.FAILED);
        doThrow(new IllegalStateException("conflict")).when(transferResultService).confirm("REF-7", "reconcile-REF-7");

        service.reconcileStaleTransfers();

        verify(transferResultService).fail("REF-8", "reconcile-REF-8");
    }
}
