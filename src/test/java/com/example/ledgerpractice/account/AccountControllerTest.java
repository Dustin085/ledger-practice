package com.example.ledgerpractice.account;

import com.example.ledgerpractice.journal.JournalEntryLineRepository;
import com.example.ledgerpractice.journal.JournalEntryLineSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class AccountControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private JournalEntryLineRepository journalEntryLineRepository;

    private Account mockAccount;
    private JournalEntryLineSummary mockJournalEntryLineSummary;

    private void stubAccount() {
        mockAccount = Account.builder()
                .id(0L)
                .code("1101")
                .name("Mock Account")
                .type(AccountType.ASSET)
                .externalSettlement(false)
                .build();
        when(accountRepository.findAll()).thenReturn(List.of(mockAccount));
        when(accountRepository.findById(0L)).thenReturn(Optional.of(mockAccount));
        mockJournalEntryLineSummary = new JournalEntryLineSummary(
                0L,
                LocalDate.now(),
                BigDecimal.TEN,
                BigDecimal.ZERO,
                "Mock Journal Entry Line"
        );
        when(journalEntryLineRepository.findSummaryByAccountId(0L)).thenReturn(List.of(mockJournalEntryLineSummary));
    }

    @Test
    void happyPathList() throws Exception {
        stubAccount();
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(view().name("/pages/accounts/list"))
                .andExpect(model().attributeExists("accounts"))
                .andExpect(model().attribute("accounts", List.of(mockAccount)));
    }

    @Test
    void findByIdWithValidId() throws Exception {
        stubAccount();
        mockMvc.perform(get("/accounts/{id}", 0L))
                .andExpect(status().isOk())
                .andExpect(view().name("/pages/accounts/detail"))
                .andExpect(model().attributeExists("account"))
                .andExpect(model().attribute("account", mockAccount))
                .andExpect(model().attribute("lines", List.of(mockJournalEntryLineSummary)));
    }

    @Test
    void findByIdWithInvalidId() throws Exception {
        mockMvc.perform(get("/accounts/{id}", 1L))
                .andExpect(status().isNotFound());
    }
}