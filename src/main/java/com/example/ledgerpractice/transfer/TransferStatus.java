package com.example.ledgerpractice.transfer;

public enum TransferStatus {
    /** 已建立，尚未送出給外部服務。 */
    CREATED,
    /** 已透過 Outbox 送出，等待外部服務回覆。 */
    SUBMITTED,
    /** 外部服務確認成功。 */
    CONFIRMED,
    /** 外部服務回覆失敗，等待補償。 */
    FAILED,
    /** 補償動作（沖銷分錄）進行中。 */
    COMPENSATING,
    /** 補償完成。 */
    COMPENSATED
}
