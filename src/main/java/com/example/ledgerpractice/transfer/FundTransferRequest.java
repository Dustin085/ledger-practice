package com.example.ledgerpractice.transfer;

import com.example.ledgerpractice.account.Account;
import com.example.ledgerpractice.journal.JournalEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

// Saga 的持久化狀態機：協調「內部分錄」跟「外部資金服務」這兩個無法用同一個 DB transaction
// 包起來的邊界。orchestrator（service 層）根據 status 決定下一步該送出、確認還是補償。
@Entity
@Table(name = "fund_transfer_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundTransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false, unique = true)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account sourceAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // 外部對象的簡化識別（例如收款銀行帳號末四碼），不另建 entity，避免範圍膨脹。
    @Column(name = "external_counterparty", nullable = false, length = 200)
    private String externalCounterparty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransferStatus status = TransferStatus.CREATED;

    // 外部服務回傳的交易編號；送出前是 null，用來做查詢/對帳/冪等比對。
    @Column(name = "external_reference_id", length = 100, unique = true)
    private String externalReferenceId;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
