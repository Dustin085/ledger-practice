package com.example.ledgerpractice.ledger;

public enum SubmitOutcome {
    SUBMITTED,        // 這次成功送出，OutboxRelay 應該標記 published
    ALREADY_HANDLED,  // 狀態已經不是 CREATED，不用重做，但一樣該標記 published
    FAILED            // 外部服務呼叫失敗，OutboxRelay 不要標記 published，留給下一輪重試
}
