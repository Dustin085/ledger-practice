package com.example.ledgerpractice.account;

import com.example.ledgerpractice.exception.EntityNotFoundException;
import com.example.ledgerpractice.journal.JournalEntryLineRepository;
import com.example.ledgerpractice.journal.JournalEntryLineSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountRepository accountRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("accounts", accountRepository.findAll());

        return "/pages/accounts/list";
    }

    @GetMapping("/{id}")
    public String findById(@PathVariable Long id, Model model) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(Account.class, id));
        model.addAttribute("account", account);
        List<JournalEntryLineSummary> lines = journalEntryLineRepository.findSummaryByAccountId(id);
        model.addAttribute("lines", lines);

        return "/pages/accounts/detail";
    }
}
