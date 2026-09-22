package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.account.AccountRepository;
import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.transfer.JournalLineRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final AccountRepository accountRepository;

    @GetMapping("/journal-entries/new")
    public String newForm(Model model) {
        TransferForm form = new TransferForm();
        form.setEntryDate(LocalDate.now());
        form.getLines().add(new LineForm());
        form.getLines().add(new LineForm());
        model.addAttribute("transferForm", form);
        model.addAttribute("accounts", accountRepository.findAll());
        return "journal-entry-form";
    }

    @PostMapping("/journal-entries")
    public String create(@Valid @ModelAttribute("transferForm") TransferForm form,
                          BindingResult bindingResult,
                          Model model) {
        // 不管等一下走哪條路徑回到表單（bean validation 失敗、或借貸不平衡），
        // 樣板都需要這份清單畫下拉選單，先統一補上，避免漏掉某條路徑。
        model.addAttribute("accounts", accountRepository.findAll());

        if (bindingResult.hasErrors()) {
            return "journal-entry-form";
        }

        List<JournalLineRequest> lines = new ArrayList<>();
        for (LineForm line : form.getLines()) {
            lines.add(new JournalLineRequest(
                    line.getAccountId(),
                    line.getDebitAmount(),
                    line.getCreditAmount(),
                    line.getMemo()));
        }

        try {
            JournalEntry journalEntry = ledgerService.initiateTransfer(
                    form.getEntryDate(),
                    form.getDescription(),
                    lines,
                    form.getExternalCounterparty());
            model.addAttribute("journalEntry", journalEntry);
            return "journal-entry-result";
        } catch (UnbalancedJournalEntryException | IllegalArgumentException | IllegalStateException e) {
            // 比照 JSP-practice 的做法：驗證失敗時不清空使用者輸入，重新渲染同一個 form
            // （transferForm 已經是這個 method 的參數，Spring 會自動放回 Model）。
            model.addAttribute("errorMessage", e.getMessage());
            return "journal-entry-form";
        }
    }
}
