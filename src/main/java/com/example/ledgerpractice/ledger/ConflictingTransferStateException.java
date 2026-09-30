package com.example.ledgerpractice.ledger;

import com.example.ledgerpractice.transfer.TransferStatus;
import lombok.Getter;

// 兩個外部通知互相矛盾時丟出來（例如已經因為 FAILED 補償過，又收到一個 CONFIRMED）：
// 代表錢的實際狀態不確定，需要人工介入，不是程式錯誤，也不該重試。
@Getter
public class ConflictingTransferStateException extends RuntimeException {

    private final String externalReferenceId;
    private final TransferStatus actualStatus;

    public ConflictingTransferStateException(String externalReferenceId, TransferStatus actualStatus) {
        super("Expected SUBMITTED but was " + actualStatus + " for externalReferenceId " + externalReferenceId
                + ". This indicates conflicting settlement results and requires manual reconciliation before this transfer can proceed.");
        this.externalReferenceId = externalReferenceId;
        this.actualStatus = actualStatus;
    }
}
