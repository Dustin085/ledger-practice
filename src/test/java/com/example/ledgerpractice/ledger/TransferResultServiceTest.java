package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.journal.JournalEntryStatus;
import com.example.ledgerpractice.outbox.InboxEvent;
import com.example.ledgerpractice.outbox.InboxEventRepository;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferResultServiceTest {

    @Mock
    private FundTransferRequestRepository fundTransferRequestRepository;
    @Mock
    private InboxEventRepository inboxEventRepository;
    @Mock
    private CompensationService compensationService;

    private TransferResultService transferResultService;

    @BeforeEach
    void setUp() {
        transferResultService = new TransferResultService(
                fundTransferRequestRepository,
                inboxEventRepository,
                new ObjectMapper(),
                compensationService);
    }

    private FundTransferRequest submittedRequest() {
        JournalEntry journalEntry = JournalEntry.builder()
                .id(10L)
                .status(JournalEntryStatus.PENDING)
                .build();
        return FundTransferRequest.builder()
                .id(1L)
                .journalEntry(journalEntry)
                .externalReferenceId("MOCK-ref")
                .status(TransferStatus.SUBMITTED)
                .build();
    }

    @Test
    void confirmAlreadyProcessedDoesNothing() {
        FundTransferRequest request = submittedRequest();
        when(fundTransferRequestRepository.findByExternalReferenceIdForUpdate("MOCK-ref"))
                .thenReturn(Optional.of(request));
        when(inboxEventRepository.findByExternalEventId("evt-1"))
                .thenReturn(Optional.of(InboxEvent.builder().externalEventId("evt-1").build()));

        transferResultService.confirm("MOCK-ref", "evt-1");

        assertThat(request.getStatus()).isEqualTo(TransferStatus.SUBMITTED);
        assertThat(request.getJournalEntry().getStatus()).isEqualTo(JournalEntryStatus.PENDING);
        verifyNoInteractions(compensationService);
        verify(inboxEventRepository, never()).save(any());
    }

    @Test
    void confirmMarksTransferConfirmedAndJournalEntryPosted() {
        FundTransferRequest request = submittedRequest();
        when(inboxEventRepository.findByExternalEventId("evt-1")).thenReturn(Optional.empty());
        when(fundTransferRequestRepository.findByExternalReferenceIdForUpdate("MOCK-ref"))
                .thenReturn(Optional.of(request));

        transferResultService.confirm("MOCK-ref", "evt-1");

        assertThat(request.getStatus()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(request.getJournalEntry().getStatus()).isEqualTo(JournalEntryStatus.POSTED);
        verify(inboxEventRepository).save(any());
        verifyNoInteractions(compensationService);
    }

    @Test
    void confirmRequestNotFoundThrowsIllegalArgumentException() {
        when(fundTransferRequestRepository.findByExternalReferenceIdForUpdate("MOCK-ref"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferResultService.confirm("MOCK-ref", "evt-1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(inboxEventRepository, never()).save(any());
    }

    @Test
    void confirmRequestNotSubmittedThrowsIllegalStateException() {
        FundTransferRequest request = submittedRequest();
        request.setStatus(TransferStatus.CREATED);
        when(inboxEventRepository.findByExternalEventId("evt-1")).thenReturn(Optional.empty());
        when(fundTransferRequestRepository.findByExternalReferenceIdForUpdate("MOCK-ref"))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> transferResultService.confirm("MOCK-ref", "evt-1"))
                .isInstanceOf(IllegalStateException.class);

        verify(inboxEventRepository, never()).save(any());
    }

    @Test
    void failAlreadyProcessedDoesNothing() {
        FundTransferRequest request = submittedRequest();
        when(fundTransferRequestRepository.findByExternalReferenceIdForUpdate("MOCK-ref"))
                .thenReturn(Optional.of(request));
        when(inboxEventRepository.findByExternalEventId("evt-2"))
                .thenReturn(Optional.of(InboxEvent.builder().externalEventId("evt-2").build()));

        transferResultService.fail("MOCK-ref", "evt-2");

        assertThat(request.getStatus()).isEqualTo(TransferStatus.SUBMITTED);
        verifyNoInteractions(compensationService);
        verify(inboxEventRepository, never()).save(any());
    }

    @Test
    void failTriggersCompensationAndRecordsInboxEvent() {
        FundTransferRequest request = submittedRequest();
        when(inboxEventRepository.findByExternalEventId("evt-2")).thenReturn(Optional.empty());
        when(fundTransferRequestRepository.findByExternalReferenceIdForUpdate("MOCK-ref"))
                .thenReturn(Optional.of(request));

        transferResultService.fail("MOCK-ref", "evt-2");

        verify(compensationService).compensate(request);
        verify(inboxEventRepository).save(any());
    }

    @Test
    void confirmWhenAlreadyConfirmedIsTreatedAsSuccessAndRecordsEvent() {
        FundTransferRequest request = submittedRequest();
        request.setStatus(TransferStatus.CONFIRMED);
        when(inboxEventRepository.findByExternalEventId("late-evt")).thenReturn(Optional.empty());
        when(fundTransferRequestRepository.findByExternalReferenceIdForUpdate("MOCK-ref"))
                .thenReturn(Optional.of(request));

        transferResultService.confirm("MOCK-ref", "late-evt");

        assertThat(request.getStatus()).isEqualTo(TransferStatus.CONFIRMED);
        verify(inboxEventRepository).save(any());
        verifyNoInteractions(compensationService);
    }

    @Test
    void confirmWhenAlreadyCompensatedIsAConflictAndThrows() {
        FundTransferRequest request = submittedRequest();
        request.setStatus(TransferStatus.COMPENSATED);
        when(inboxEventRepository.findByExternalEventId("evt-x")).thenReturn(Optional.empty());
        when(fundTransferRequestRepository.findByExternalReferenceIdForUpdate("MOCK-ref"))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> transferResultService.confirm("MOCK-ref", "evt-x"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(request.getStatus()).isEqualTo(TransferStatus.COMPENSATED);
        verify(inboxEventRepository, never()).save(any());
    }

    @Test
    void failWhenAlreadyCompensatedIsTreatedAsSuccessAndDoesNotCompensateAgain() {
        FundTransferRequest request = submittedRequest();
        request.setStatus(TransferStatus.COMPENSATED);
        when(inboxEventRepository.findByExternalEventId("late-evt")).thenReturn(Optional.empty());
        when(fundTransferRequestRepository.findByExternalReferenceIdForUpdate("MOCK-ref"))
                .thenReturn(Optional.of(request));

        transferResultService.fail("MOCK-ref", "late-evt");

        verify(inboxEventRepository).save(any());
        verifyNoInteractions(compensationService);
    }

    @Test
    void failWhenAlreadyConfirmedIsAConflictAndThrows() {
        FundTransferRequest request = submittedRequest();
        request.setStatus(TransferStatus.CONFIRMED);
        when(inboxEventRepository.findByExternalEventId("evt-y")).thenReturn(Optional.empty());
        when(fundTransferRequestRepository.findByExternalReferenceIdForUpdate("MOCK-ref"))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> transferResultService.fail("MOCK-ref", "evt-y"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(compensationService);
        verify(inboxEventRepository, never()).save(any());
    }
}
