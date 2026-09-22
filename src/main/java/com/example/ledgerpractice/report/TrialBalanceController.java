package com.example.ledgerpractice.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/report")
@RequiredArgsConstructor
public class TrialBalanceController {
    private final TrialBalanceMapper trialBalanceMapper;

    @GetMapping("/trial-balance")
    public String getTrialBalance(Model model) {
        List<TrialBalanceRow> balances = trialBalanceMapper.findTrialBalance();
        BigDecimal totalDebit = balances.stream()
                .map(TrialBalanceRow::totalDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = balances.stream()
                .map(TrialBalanceRow::totalCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("balances", balances);
        model.addAttribute("totalDebit", totalDebit);
        model.addAttribute("totalCredit", totalCredit);

        return "/pages/trial-balance/report";
    }
}
