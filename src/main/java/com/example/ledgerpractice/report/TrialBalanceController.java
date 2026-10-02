package com.example.ledgerpractice.report;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.PrintWriter;
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

    @GetMapping("/trial-balance.csv")
    public void exportTrialBalanceCSV(
            HttpServletResponse response,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) throws IOException {
        List<TrialBalanceRow> balances = trialBalanceMapper.findTrialBalance(asOfDate);
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=trial-balance.csv");
        PrintWriter writer = response.getWriter();
        writer.write("\uFEFF");
        writer.write("編號,名稱,類型,借方,貸方" + "\r\n");
        for (TrialBalanceRow balance : balances) {
            writer.write(escapeCsvField(balance.accountCode()) + ","
                    + escapeCsvField(balance.accountName()) + ","
                    + escapeCsvField(balance.accountType().toString()) + ","
                    + balance.totalDebit() + ","
                    + balance.totalCredit() + "\r\n");
        }
    }

    private String escapeCsvField(String value) {
        String escaped = value;
        if (escaped.contains(",") ||
                escaped.contains("\n") || // 包含 \r\n
                escaped.contains("\"")) {
            escaped = escaped.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
