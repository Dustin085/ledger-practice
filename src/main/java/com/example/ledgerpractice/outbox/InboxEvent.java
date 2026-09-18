package com.example.ledgerpractice.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Inbox pattern：外部服務的回呼（webhook/MQ）在真實世界是 at-least-once，可能重送。靠
// externalEventId 的 unique constraint 去重，避免同一筆「轉帳成功」通知被處理兩次、分錄被過帳兩次。
@Entity
@Table(name = "inbox_event", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = "external_event_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_event_id", nullable = false, length = 100)
    private String externalEventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;
}
