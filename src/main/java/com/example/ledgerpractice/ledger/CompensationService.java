package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.journal.JournalEntry;
import com.example.ledgerpractice.journal.JournalEntryLine;
import com.example.ledgerpractice.journal.JournalEntryRepository;
import com.example.ledgerpractice.journal.JournalEntryStatus;
import com.example.ledgerpractice.transfer.FundTransferRequest;
import com.example.ledgerpractice.transfer.TransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// 「補償」這個動作本身，跟誰觸發它無關——可能是外部明確回報失敗
// (TransferResultService)，也可能是重試次數用完主動放棄 (TransferSubmissionService)。
// 兩種呼叫方呼叫這裡的時候，自己身上都已經有一個開著的 transaction
// （fail() 的 REQUIRED、submit() 的 REQUIRES_NEW）。這裡的 @Transactional
// 沒有指定 propagation，預設是 REQUIRED，會直接加入呼叫方現有的 transaction，
// 不會另外開一個——效果等同沒標註，純粹是讓「這個方法要求要在 transaction 裡跑」
// 這件事寫在程式碼上，不用只靠註解交代。呼叫方必須自己先鎖好 FundTransferRequest
// 並確認過狀態，這裡只負責把「原分錄 REVERSED + 補一筆沖銷分錄
// + FundTransferRequest 轉 COMPENSATED」這件事做完。
@Service
@RequiredArgsConstructor
class CompensationService {

    private final JournalEntryRepository journalEntryRepository;

    @Transactional
    public void compensate(FundTransferRequest request) {
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
