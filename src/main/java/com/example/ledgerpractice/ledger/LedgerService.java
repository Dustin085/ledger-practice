package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.account.Account;
import com.example.ledgerpractice.account.AccountRepository;
import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.journal.JournalEntryLine;
import com.example.ledgerpractice.journal.JournalEntryRepository;
import com.example.ledgerpractice.journal.JournalEntryStatus;
import com.example.ledgerpractice.outbox.FundTransferRequestedPayload;
import com.example.ledgerpractice.outbox.OutboxEvent;
import com.example.ledgerpractice.outbox.OutboxEventRepository;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.FundTransferRequestRepository;
import com.example.ledgerpractice.transfer.JournalLineRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final JournalEntryRepository journalEntryRepository;
    private final FundTransferRequestRepository fundTransferRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public JournalEntry initiateTransfer(
            LocalDate entryDate,
            String description,
            List<JournalLineRequest> lines,
            String externalCounterparty) {

        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("分錄至少要有一筆明細");
        }

        JournalEntry journalEntry = JournalEntry.builder()
                .entryDate(entryDate)
                .description(description)
                .build();

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        Account externalAccount = null;
        BigDecimal externalAmount = null;

        for (JournalLineRequest line : lines) {
            Account account = accountRepository.findById(line.accountId())
                    .orElseThrow(() -> new IllegalArgumentException("科目不存在：" + line.accountId()));

            JournalEntryLine journalEntryLine = JournalEntryLine.builder()
                    .account(account)
                    .debitAmount(line.debitAmount())
                    .creditAmount(line.creditAmount())
                    .memo(line.memo())
                    .build();
            journalEntry.addLine(journalEntryLine);

            totalDebit = totalDebit.add(line.debitAmount());
            totalCredit = totalCredit.add(line.creditAmount());

            if (account.isExternalSettlement()) {
                if (externalAccount != null) {
                    // 先求端到端能動的垂直切片：目前只處理一筆分錄對應一個外部資金科目，
                    // 多筆外部腿的情境留到之後再擴充。
                    throw new IllegalStateException("目前只支援一筆分錄對應一個外部資金科目");
                }
                externalAccount = account;
                externalAmount = line.debitAmount().signum() != 0
                        ? line.debitAmount()
                        : line.creditAmount();
            }
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new UnbalancedJournalEntryException(totalDebit, totalCredit);
        }

        if (externalAccount == null) {
            // 純內部分錄，沒有外部資金要等待確認，不需要進 PENDING，直接生效。
            journalEntry.setStatus(JournalEntryStatus.POSTED);
            return journalEntryRepository.save(journalEntry);
        }

        journalEntry = journalEntryRepository.save(journalEntry);

        FundTransferRequest fundTransferRequest = FundTransferRequest.builder()
                .journalEntry(journalEntry)
                .sourceAccount(externalAccount)
                .amount(externalAmount)
                .externalCounterparty(externalCounterparty)
                .build();
        fundTransferRequest = fundTransferRequestRepository.save(fundTransferRequest);

        FundTransferRequestedPayload payload = new FundTransferRequestedPayload(
                fundTransferRequest.getId(),
                journalEntry.getId(),
                fundTransferRequest.getAmount(),
                fundTransferRequest.getExternalCounterparty());

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("FundTransferRequest")
                .aggregateId(fundTransferRequest.getId())
                .eventType("FundTransferRequested")
                .payload(objectMapper.writeValueAsString(payload))
                .build();
        outboxEventRepository.save(outboxEvent);

        return journalEntry;
    }
}
