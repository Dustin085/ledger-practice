package com.example.ledgerpractice.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 依 id 升冪並限制筆數：每輪工作量有上限，長時間中斷後的大量積壓不會一次全撈進記憶體，
    // 而且發布順序穩定（依建立順序）。
    List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
