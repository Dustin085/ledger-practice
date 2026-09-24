package com.example.ledgerpractice.outbox;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String OUTBOX_EXCHANGE_NAME = "outbox.events";
    public static final String SUBMISSION_QUEUE_NAME = "fund-transfer.requested.submission";
    public static final String AUDIT_LOG_QUEUE_NAME = "fund-transfer.requested.audit-log";
    public static final String DEAD_LETTER_EXCHANGE_NAME = "outbox.events.dlx";
    public static final String SUBMISSION_DEAD_LETTER_QUEUE_NAME = "fund-transfer.requested.submission.dlq";
    private static final String SUBMISSION_DEAD_LETTER_ROUTING_KEY = "submission.dead";
    public static final String EXTERNAL_SETTLEMENT_EXCHANGE_NAME = "external.settlement.events";
    public static final String TRANSFER_RESULT_QUEUE_NAME = "transfer.result.inbox";
    public static final String TRANSFER_RESULT_DEAD_LETTER_QUEUE_NAME = "transfer.result.inbox.dlq";
    public static final String TRANSFER_CONFIRMED_ROUTING_KEY = "transfer.confirmed";
    public static final String TRANSFER_FAILED_ROUTING_KEY = "transfer.failed";
    private static final String TRANSFER_RESULT_BINDING_PATTERN = "transfer.*";
    private static final String TRANSFER_RESULT_DEAD_LETTER_ROUTING_KEY = "result.dead";
    private static final String MATCH_ALL_ROUTING_KEY = "#";

    @Bean
    public TopicExchange outboxEventsExchange() {
        return new TopicExchange(OUTBOX_EXCHANGE_NAME, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue fundTransferSubmissionQueue() {
        return QueueBuilder.durable(SUBMISSION_QUEUE_NAME)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .deadLetterRoutingKey(SUBMISSION_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue submissionDeadLetterQueue() {
        return new Queue(SUBMISSION_DEAD_LETTER_QUEUE_NAME, true);
    }

    @Bean
    public Binding submissionDeadLetterBinding(Queue submissionDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(submissionDeadLetterQueue)
                .to(deadLetterExchange)
                .with(SUBMISSION_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Queue auditLogQueue() {
        return new Queue(AUDIT_LOG_QUEUE_NAME, true);
    }

    @Bean
    public Binding fundTransferSubmissionBinding(Queue fundTransferSubmissionQueue, TopicExchange outboxEventsExchange) {
        return BindingBuilder.bind(fundTransferSubmissionQueue)
                .to(outboxEventsExchange)
                .with(MATCH_ALL_ROUTING_KEY);
    }

    @Bean
    public Binding auditLogBinding(Queue auditLogQueue, TopicExchange outboxEventsExchange) {
        return BindingBuilder.bind(auditLogQueue)
                .to(outboxEventsExchange)
                .with(MATCH_ALL_ROUTING_KEY);
    }

    @Bean
    public TopicExchange externalSettlementExchange() {
        return new TopicExchange(EXTERNAL_SETTLEMENT_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue transferResultQueue() {
        return QueueBuilder.durable(TRANSFER_RESULT_QUEUE_NAME)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .deadLetterRoutingKey(TRANSFER_RESULT_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue transferResultDeadLetterQueue() {
        return new Queue(TRANSFER_RESULT_DEAD_LETTER_QUEUE_NAME, true);
    }

    @Bean
    public Binding transferResultBinding(Queue transferResultQueue, TopicExchange externalSettlementExchange) {
        return BindingBuilder.bind(transferResultQueue)
                .to(externalSettlementExchange)
                .with(TRANSFER_RESULT_BINDING_PATTERN);
    }

    @Bean
    public Binding transferResultDeadLetterBinding(Queue transferResultDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(transferResultDeadLetterQueue)
                .to(deadLetterExchange)
                .with(TRANSFER_RESULT_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter("com.example.ledgerpractice");
    }
}
