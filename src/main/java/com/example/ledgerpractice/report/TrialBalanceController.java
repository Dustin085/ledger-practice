package com.example.ledgerpractice.report;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/report")
@RequiredArgsConstructor
public class TrialBalanceController {
    private final TrialBalanceMapper trialBalanceMapper;

    @GetMapping("/trial-balance")
    public String getTrialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            Model model) {
        List<TrialBalanceRow> balances = trialBalanceMapper.findTrialBalance(asOfDate);
        BigDecimal totalDebit = balances.stream()
                .map(TrialBalanceRow::totalDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = balances.stream()
                .map(TrialBalanceRow::totalCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("balances", balances);
        model.addAttribute("totalDebit", totalDebit);
        model.addAttribute("totalCredit", totalCredit);
        model.addAttribute("asOfDate", asOfDate);

        return "/pages/trial-balance/report";
    }
}
