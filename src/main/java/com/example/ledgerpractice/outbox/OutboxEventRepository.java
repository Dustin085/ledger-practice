package com.example.ledgerpractice.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.hibernate.Timeouts;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 依 id 升冪並限制筆數：每輪工作量有上限，長時間中斷後的大量積壓不會一次全撈進記憶體，
    // 而且發布順序穩定（依建立順序）。
    // FOR UPDATE SKIP LOCKED：@QueryHint 的 value 只能是編譯期常數字串，不能呼叫
    // String.valueOf(...)，用字串串接 "" + Timeouts.SKIP_LOCKED_MILLI 讓編譯器把它
    // 摺成常數。舊的 LockOptions.SKIP_LOCKED 已經被標記 @Deprecated(forRemoval=true)，
    // 改用還沒被棄用的 Timeouts.SKIP_LOCKED_MILLI。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "" + Timeouts.SKIP_LOCKED_MILLI)})
    List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "" + Timeouts.SKIP_LOCKED_MILLI)})
    Optional<OutboxEvent> findByIdAndPublishedAtIsNull(Long id);

    long countByPublishedAtIsNull();

    Optional<OutboxEvent> findFirstByPublishedAtIsNullOrderByIdAsc();
}
