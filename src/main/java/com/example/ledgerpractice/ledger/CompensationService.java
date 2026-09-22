package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.journal.JournalEntryLine;
import com.example.ledgerpractice.journal.JournalEntryRepository;
import com.example.ledgerpractice.journal.JournalEntryStatus;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.TransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

// 「補償」這個動作本身，跟誰觸發它無關——可能是外部明確回報失敗
// (TransferResultService)，也可能是重試次數用完主動放棄 (TransferSubmissionService)。
// 兩種呼叫方手上已經鎖住的 request、所在的 transaction 都不一樣，所以這裡刻意不加
// @Transactional：永遠只參與呼叫方目前已經開啟的 transaction，呼叫方必須自己先鎖好
// FundTransferRequest 並確認過狀態，這裡只負責把「原分錄 REVERSED + 補一筆沖銷分錄
// + FundTransferRequest 轉 COMPENSATED」這件事做完。
@Service
@RequiredArgsConstructor
class CompensationService {

    private final JournalEntryRepository journalEntryRepository;

    void compensate(FundTransferRequest request) {
        JournalEntry original = request.getJournalEntry();

        request.setStatus(TransferStatus.COMPENSATED);
        original.setStatus(JournalEntryStatus.REVERSED);

        journalEntryRepository.save(buildReversalEntry(original));
    }

    private JournalEntry buildReversalEntry(JournalEntry original) {
        JournalEntry reversal = JournalEntry.builder()
                .entryDate(LocalDate.now())
                .description("沖銷：" + original.getDescription())
                .reversalOfEntryId(original.getId())
                .status(JournalEntryStatus.POSTED)
                .build();

        for (JournalEntryLine line : original.getLines()) {
            JournalEntryLine reversalLine = JournalEntryLine.builder()
                    .account(line.getAccount())
                    .debitAmount(line.getCreditAmount())
                    .creditAmount(line.getDebitAmount())
                    .memo("沖銷：" + (line.getMemo() == null ? "" : line.getMemo()))
                    .build();
            reversal.addLine(reversalLine);
        }

        return reversal;
    }
}
