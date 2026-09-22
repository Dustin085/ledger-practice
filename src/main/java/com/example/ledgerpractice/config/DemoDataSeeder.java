package com.example.ledgerpractice.config;

import com.example.ledgerpractice.account.AccountRepository;
import com.example.ledgerpractice.journal.JournalEntryRepository;
import com.example.ledgerpractice.ledger.LedgerService;
import com.example.ledgerpractice.transfer.JournalLineRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 開發/測試用的示範資料：涵蓋「純內部分錄」「會牽涉外部服務、正常會成功」
// 「送出就會失敗、示範永遠重試」三種情境，開機就能直接去 /transfers/pending
// 或 H2 console 看東西，不用每次重啟都手動建一輪分錄。
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    @Override
    public void run(String... args) {
        if (journalEntryRepository.count() > 0) {
            return;
        }

        Long cash = accountId("1101");
        Long bank = accountId("1102");
        Long equity = accountId("3101");
        Long payroll = accountId("5101");

        // 純內部分錄，沒有外部科目，直接 POSTED。
        ledgerService.initiateTransfer(
                LocalDate.now(),
                "demo：股東出資",
                List.of(
                        new JournalLineRequest(cash, new BigDecimal("100000"), BigDecimal.ZERO, "出資入帳"),
                        new JournalLineRequest(equity, BigDecimal.ZERO, new BigDecimal("100000"), "股本增加")
                ),
                null);

        // 牽涉外部科目，正常情況：等 OutboxRelay 處理過後會出現在 /transfers/pending。
        ledgerService.initiateTransfer(
                LocalDate.now(),
                "demo：薪資轉帳（正常）",
                List.of(
                        new JournalLineRequest(payroll, new BigDecimal("5000"), BigDecimal.ZERO, "九月薪資"),
                        new JournalLineRequest(bank, BigDecimal.ZERO, new BigDecimal("5000"), "銀行撥款")
                ),
                "demo-employee-ok");

        // externalCounterparty 帶 FAIL，MockExternalPaymentGateway 送出會一直失敗，
        // 示範 OutboxRelay 無限重試的情境，之後做重試上限時可以直接拿這筆測。
        ledgerService.initiateTransfer(
                LocalDate.now(),
                "demo：薪資轉帳（送出會失敗）",
                List.of(
                        new JournalLineRequest(payroll, new BigDecimal("3000"), BigDecimal.ZERO, "九月薪資"),
                        new JournalLineRequest(bank, BigDecimal.ZERO, new BigDecimal("3000"), "銀行撥款")
                ),
                "demo-employee-FAIL");
    }

    private Long accountId(String code) {
        return accountRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Seed account not found: " + code))
                .getId();
    }
}
