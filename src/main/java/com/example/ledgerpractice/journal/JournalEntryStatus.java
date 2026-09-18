package com.example.ledgerpractice.journal;

public enum JournalEntryStatus {
    /** 借貸平衡已驗證、已寫入 DB，等待外部資金確認（未清算）。 */
    PENDING,
    /** 外部資金確認成功，正式生效，計入試算表。 */
    POSTED,
    /** 外部資金確認失敗，尚未產生沖銷分錄前的過渡狀態。 */
    FAILED,
    /** 已用沖銷分錄補償，這筆本身不再計入試算表。 */
    REVERSED
}
