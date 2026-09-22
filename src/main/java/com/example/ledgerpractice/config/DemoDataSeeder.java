package com.example.ledgerpractice.config;

import com.example.ledgerpractice.account.AccountRepository;
import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.journal.JournalEntryRepository;
import com.example.ledgerpractice.ledger.LedgerService;
import com.example.ledgerpractice.ledger.TransferResultService;
import com.example.ledgerpractice.ledger.TransferSubmissionService;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.JournalLineRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 開發/測試用的示範資料：涵蓋五大類科目都有活動、外部轉帳的三種常見終局
// （還在等待、自動補償、正常確認完成），開機就能直接去 /transfers/pending、
// /report/trial-balance 看東西，不用每次重啟都手動建一輪分錄。
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final FundTransferRequestRepository fundTransferRequestRepository;
    private final LedgerService ledgerService;
    private final TransferSubmissionService transferSubmissionService;
    private final TransferResultService transferResultService;

    @Override
    public void run(String... args) {
        if (journalEntryRepository.count() > 0) {
            return;
        }

        Long cash = accountId("1101");
        Long bank = accountId("1102");
        Long receivable = accountId("1103");
        Long payable = accountId("2101");
        Long equity = accountId("3101");
        Long revenue = accountId("4101");
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

        // 純內部分錄：現金收現的服務收入，讓 REVENUE 科目也有活動。
        ledgerService.initiateTransfer(
                LocalDate.now(),
                "demo：現金收現服務收入",
                List.of(
                        new JournalLineRequest(cash, new BigDecimal("8000"), BigDecimal.ZERO, "收現"),
                        new JournalLineRequest(revenue, BigDecimal.ZERO, new BigDecimal("8000"), "服務收入")
                ),
                null);

        // 純內部分錄：服務先做、款項還沒收，讓 應收帳款 也有活動。
        ledgerService.initiateTransfer(
                LocalDate.now(),
                "demo：服務收入（應收帳款）",
                List.of(
                        new JournalLineRequest(receivable, new BigDecimal("6000"), BigDecimal.ZERO, "應收客戶款項"),
                        new JournalLineRequest(revenue, BigDecimal.ZERO, new BigDecimal("6000"), "服務收入")
                ),
                null);

        // 純內部分錄：薪資費用已發生但還沒實際撥款，讓 應付帳款 也有活動。
        ledgerService.initiateTransfer(
                LocalDate.now(),
                "demo：薪資費用（應付帳款，尚未撥款）",
                List.of(
                        new JournalLineRequest(payroll, new BigDecimal("2000"), BigDecimal.ZERO, "十月薪資提列"),
                        new JournalLineRequest(payable, BigDecimal.ZERO, new BigDecimal("2000"), "應付薪資")
                ),
                null);

        // 牽涉外部科目，正常情況：等 OutboxRelay 處理過後會出現在 /transfers/pending，
        // 停在 SUBMITTED，故意不在這裡模擬回覆，讓你自己去畫面上點。
        ledgerService.initiateTransfer(
                LocalDate.now(),
                "demo：薪資轉帳（等待外部確認）",
                List.of(
                        new JournalLineRequest(payroll, new BigDecimal("5000"), BigDecimal.ZERO, "九月薪資"),
                        new JournalLineRequest(bank, BigDecimal.ZERO, new BigDecimal("5000"), "銀行撥款")
                ),
                "demo-employee-ok");

        // externalCounterparty 帶 FAIL，MockExternalPaymentGateway 送出會一直失敗，
        // 示範 OutboxRelay 重試上限用完自動放棄並補償的情境。
        ledgerService.initiateTransfer(
                LocalDate.now(),
                "demo：薪資轉帳（送出會失敗）",
                List.of(
                        new JournalLineRequest(payroll, new BigDecimal("3000"), BigDecimal.ZERO, "九月薪資"),
                        new JournalLineRequest(bank, BigDecimal.ZERO, new BigDecimal("3000"), "銀行撥款")
                ),
                "demo-employee-FAIL");

        // 牽涉外部科目，直接在種子資料裡把整個 saga 走完（送出 -> 外部確認成功），
        // 示範「正常結束」長什麼樣子，不用等排程也不用手動點按鈕。
        JournalEntry confirmedEntry = ledgerService.initiateTransfer(
                LocalDate.now(),
                "demo：薪資轉帳（已完成確認）",
                List.of(
                        new JournalLineRequest(payroll, new BigDecimal("4000"), BigDecimal.ZERO, "十月薪資"),
                        new JournalLineRequest(bank, BigDecimal.ZERO, new BigDecimal("4000"), "銀行撥款")
                ),
                "demo-employee-confirmed");
        FundTransferRequest confirmedRequest = fundTransferRequestRepository
                .findByJournalEntryId(confirmedEntry.getId())
                .orElseThrow();
        transferSubmissionService.submit(confirmedRequest.getId());
        String confirmedReferenceId = fundTransferRequestRepository
                .findByJournalEntryId(confirmedEntry.getId())
                .orElseThrow()
                .getExternalReferenceId();
        transferResultService.confirm(confirmedReferenceId, "demo-confirm-" + confirmedEntry.getId());
    }

    private Long accountId(String code) {
        return accountRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Seed account not found: " + code))
                .getId();
    }
}
