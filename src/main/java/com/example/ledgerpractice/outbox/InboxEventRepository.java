package com.example.ledgerpractice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InboxEventRepository extends JpaRepository<InboxEvent, Long> {

    Optional<InboxEvent> findByExternalEventId(String externalEventId);
}
