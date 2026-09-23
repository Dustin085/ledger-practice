package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.account.Account;
import com.example.ledgerpractice.account.AccountRepository;
import com.example.ledgerpractice.account.AccountType;
import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.journal.JournalEntryLine;
import com.example.ledgerpractice.journal.JournalEntryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(LedgerController.class)
class LedgerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerService ledgerService;

    @MockitoBean
    private AccountRepository accountRepository;

    private Account cash;
    private Account equity;

    private void stubAccounts() {
        cash = Account.builder().id(1L).code("1101").name("現金").type(AccountType.ASSET).build();
        equity = Account.builder().id(2L).code("3101").name("股本").type(AccountType.EQUITY).build();
        when(accountRepository.findAll()).thenReturn(List.of(cash, equity));
    }

    @Test
    void newFormShowsBlankFormWithTwoLines() throws Exception {
        stubAccounts();

        mockMvc.perform(get("/journal-entries/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/journal-entries/form"))
                .andExpect(model().attributeExists("transferForm"))
                .andExpect(model().attribute("accounts", List.of(cash, equity)));
    }

    @Test
    void createWithValidDataShowsDetailPage() throws Exception {
        stubAccounts();
        JournalEntry journalEntry = JournalEntry.builder()
                .id(1L)
                .entryDate(LocalDate.now())
                .description("股東出資")
                .status(JournalEntryStatus.POSTED)
                .build();
        journalEntry.addLine(JournalEntryLine.builder()
                .account(cash).debitAmount(BigDecimal.valueOf(1000)).creditAmount(BigDecimal.ZERO).build());
        journalEntry.addLine(JournalEntryLine.builder()
                .account(equity).debitAmount(BigDecimal.ZERO).creditAmount(BigDecimal.valueOf(1000)).build());
        when(ledgerService.initiateTransfer(any(), anyString(), any(), any())).thenReturn(journalEntry);

        mockMvc.perform(post("/journal-entries")
                        .param("entryDate", LocalDate.now().toString())
                        .param("description", "股東出資")
                        .param("lines[0].accountId", "1")
                        .param("lines[0].debitAmount", "1000")
                        .param("lines[0].creditAmount", "0")
                        .param("lines[1].accountId", "2")
                        .param("lines[1].debitAmount", "0")
                        .param("lines[1].creditAmount", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/journal-entries/detail"))
                .andExpect(model().attribute("journalEntry", journalEntry));
    }

    @Test
    void createWithBeanValidationErrorsRedisplaysForm() throws Exception {
        stubAccounts();

        mockMvc.perform(post("/journal-entries")
                        .param("description", "")
                        .param("lines[0].accountId", "1")
                        .param("lines[0].debitAmount", "1000")
                        .param("lines[0].creditAmount", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/journal-entries/form"))
                .andExpect(model().attributeHasFieldErrors("transferForm", "description"))
                .andExpect(model().attributeExists("accounts"));

        verify(ledgerService, never()).initiateTransfer(any(), any(), any(), any());
    }

    @Test
    void createWithUnbalancedEntryRedisplaysFormWithErrorMessage() throws Exception {
        stubAccounts();
        ArgumentCaptor<List<com.example.ledgerpractice.transfer.JournalLineRequest>> linesCaptor =
                ArgumentCaptor.forClass(List.class);
        when(ledgerService.initiateTransfer(any(), anyString(), linesCaptor.capture(), any()))
                .thenThrow(new UnbalancedJournalEntryException(BigDecimal.valueOf(1000), BigDecimal.valueOf(100)));

        mockMvc.perform(post("/journal-entries")
                        .param("entryDate", LocalDate.now().toString())
                        .param("description", "領錢")
                        .param("lines[0].accountId", "1")
                        .param("lines[0].debitAmount", "1000")
                        .param("lines[0].creditAmount", "0")
                        .param("lines[1].accountId", "2")
                        .param("lines[1].debitAmount", "0")
                        .param("lines[1].creditAmount", "100"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/journal-entries/form"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attributeExists("accounts"));

        assertThat(linesCaptor.getValue()).hasSize(2);
    }
}
