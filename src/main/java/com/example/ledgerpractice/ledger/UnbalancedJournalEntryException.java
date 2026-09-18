package com.example.ledgerpractice.ledger;

import java.math.BigDecimal;

// Unchecked：一定要是 RuntimeException 的子類別，@Transactional 預設只在遇到
// unchecked exception 時才會 rollback，這裡就是借貸平衡這個不變量在系統層級被強制保證的地方。
public class UnbalancedJournalEntryException extends RuntimeException {

    public UnbalancedJournalEntryException(BigDecimal totalDebit, BigDecimal totalCredit) {
        super("借貸不平衡：借方合計 " + totalDebit + "，貸方合計 " + totalCredit);
    }
}
