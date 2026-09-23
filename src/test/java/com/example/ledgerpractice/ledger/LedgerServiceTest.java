package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.account.Account;
import com.example.ledgerpractice.account.AccountRepository;
import com.example.ledgerpractice.account.AccountType;
import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.journal.JournalEntryRepository;
import com.example.ledgerpractice.journal.JournalEntryStatus;
import com.example.ledgerpractice.outbox.OutboxEventRepository;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.JournalLineRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;
    @Mock
    private FundTransferRequestRepository fundTransferRequestRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private AccountRepository accountRepository;

    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(journalEntryRepository,
                fundTransferRequestRepository,
                outboxEventRepository,
                accountRepository,
                new ObjectMapper());
    }

    @Test
    void balancedInternalEntryIsPostedImmediately() {
        Account cash = Account.builder().id(1L).code("1101").name("現金").type(AccountType.ASSET).build();
        Account equity = Account.builder().id(2L).code("3101").name("股本").type(AccountType.EQUITY).build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(cash));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(equity));
        when(journalEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        JournalEntry result = ledgerService.initiateTransfer(
                LocalDate.now(),
                "股東出資",
                List.of(
                        new JournalLineRequest(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO, ""),
                        new JournalLineRequest(2L, BigDecimal.ZERO, BigDecimal.valueOf(1000), "")
                ),
                null);

        assertThat(result.getStatus()).isEqualTo(JournalEntryStatus.POSTED);
        verifyNoInteractions(fundTransferRequestRepository, outboxEventRepository);
    }

    @Test
    void entryWithExternalAccountCreatesTransferRequestAndOutboxEvent() {
        Account payroll = Account.builder().id(1L).code("5101").name("薪資費用").type(AccountType.EXPENSE).build();
        Account bank = Account.builder().id(2L).code("1102").name("銀行存款").type(AccountType.ASSET)
                .externalSettlement(true).build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(payroll));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bank));
        when(journalEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fundTransferRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        JournalEntry result = ledgerService.initiateTransfer(
                LocalDate.now(),
                "薪資轉帳",
                List.of(
                        new JournalLineRequest(1L, BigDecimal.valueOf(5000), BigDecimal.ZERO, ""),
                        new JournalLineRequest(2L, BigDecimal.ZERO, BigDecimal.valueOf(5000), "")
                ),
                "employee-1");

        assertThat(result.getStatus()).isEqualTo(JournalEntryStatus.PENDING);
        verify(fundTransferRequestRepository).save(any());
        verify(outboxEventRepository).save(any());
    }

    @Test
    void unbalancedEntryThrowsException() {
        Account cash = Account.builder().id(1L).code("1101").name("現金").type(AccountType.ASSET).build();
        Account bank = Account.builder().id(2L).code("1102").name("銀行帳戶").type(AccountType.ASSET).build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(cash));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bank));

        JournalLineRequest line1 = new JournalLineRequest(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "");
        JournalLineRequest line2 = new JournalLineRequest(2L, BigDecimal.ZERO, BigDecimal.valueOf(100), "");

        assertThatThrownBy(() -> ledgerService.initiateTransfer(
                LocalDate.now(), "領錢", List.of(line1, line2), ""))
                .isInstanceOf(UnbalancedJournalEntryException.class);

        verifyNoInteractions(journalEntryRepository, fundTransferRequestRepository, outboxEventRepository);
    }

    @Test
    void emptyLinesThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> ledgerService.initiateTransfer(
                LocalDate.now(), "空分錄", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(accountRepository, journalEntryRepository,
                fundTransferRequestRepository, outboxEventRepository);
    }

    @Test
    void accountNotFoundThrowsIllegalArgumentException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        JournalLineRequest line = new JournalLineRequest(99L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "");

        assertThatThrownBy(() -> ledgerService.initiateTransfer(
                LocalDate.now(), "科目不存在", List.of(line), null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(journalEntryRepository, fundTransferRequestRepository, outboxEventRepository);
    }

    @Test
    void twoExternalAccountsThrowsIllegalStateException() {
        Account bank1 = Account.builder().id(1L).code("1102").name("銀行存款A").type(AccountType.ASSET)
                .externalSettlement(true).build();
        Account bank2 = Account.builder().id(2L).code("1103").name("銀行存款B").type(AccountType.ASSET)
                .externalSettlement(true).build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(bank1));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bank2));

        JournalLineRequest line1 = new JournalLineRequest(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO, "");
        JournalLineRequest line2 = new JournalLineRequest(2L, BigDecimal.ZERO, BigDecimal.valueOf(1000), "");

        assertThatThrownBy(() -> ledgerService.initiateTransfer(
                LocalDate.now(), "兩個外部科目", List.of(line1, line2), null))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(journalEntryRepository, fundTransferRequestRepository, outboxEventRepository);
    }
}
