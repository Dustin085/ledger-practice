package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.account.Account;
import com.example.ledgerpractice.account.AccountType;
import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.journal.JournalEntryLine;
import com.example.ledgerpractice.journal.JournalEntryRepository;
import com.example.ledgerpractice.journal.JournalEntryStatus;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompensationServiceTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Captor
    private ArgumentCaptor<JournalEntry> journalEntryCaptor;

    private CompensationService compensationService;

    @BeforeEach
    void setUp() {
        compensationService = new CompensationService(journalEntryRepository);
    }

    @Test
    void compensateReversesOriginalEntryAndMarksRequestCompensated() {
        Account payroll = Account.builder().id(1L).code("5101").name("薪資費用").type(AccountType.EXPENSE).build();
        Account bank = Account.builder().id(2L).code("1102").name("銀行存款").type(AccountType.ASSET)
                .externalSettlement(true).build();

        JournalEntry original = JournalEntry.builder()
                .id(10L)
                .entryDate(LocalDate.now())
                .description("薪資轉帳")
                .status(JournalEntryStatus.PENDING)
                .build();
        JournalEntryLine debitLine = JournalEntryLine.builder()
                .account(payroll)
                .debitAmount(BigDecimal.valueOf(5000))
                .creditAmount(BigDecimal.ZERO)
                .memo("九月薪資")
                .build();
        JournalEntryLine creditLine = JournalEntryLine.builder()
                .account(bank)
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(BigDecimal.valueOf(5000))
                .memo("銀行撥款")
                .build();
        original.addLine(debitLine);
        original.addLine(creditLine);

        FundTransferRequest request = FundTransferRequest.builder()
                .id(1L)
                .journalEntry(original)
                .status(TransferStatus.SUBMITTED)
                .build();

        compensationService.compensate(request);

        assertThat(request.getStatus()).isEqualTo(TransferStatus.COMPENSATED);
        assertThat(original.getStatus()).isEqualTo(JournalEntryStatus.REVERSED);

        verify(journalEntryRepository).save(journalEntryCaptor.capture());
        JournalEntry reversal = journalEntryCaptor.getValue();

        assertThat(reversal.getReversalOfEntryId()).isEqualTo(original.getId());
        assertThat(reversal.getStatus()).isEqualTo(JournalEntryStatus.POSTED);
        assertThat(reversal.getDescription()).contains(original.getDescription());
        assertThat(reversal.getLines()).hasSize(2);

        JournalEntryLine reversedPayrollLine = reversal.getLines().getFirst();
        assertThat(reversedPayrollLine.getAccount()).isEqualTo(payroll);
        assertThat(reversedPayrollLine.getDebitAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reversedPayrollLine.getCreditAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));

        JournalEntryLine reversedBankLine = reversal.getLines().get(1);
        assertThat(reversedBankLine.getAccount()).isEqualTo(bank);
        assertThat(reversedBankLine.getDebitAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(reversedBankLine.getCreditAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
