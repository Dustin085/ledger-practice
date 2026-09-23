package com.example.ledgerpractice.outbox;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_NAME = "outbox.events";
    public static final String SUBMISSION_QUEUE_NAME = "fund-transfer.requested.submission";
    private static final String MATCH_ALL_ROUTING_KEY = "#";

    @Bean
    public TopicExchange outboxEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue fundTransferSubmissionQueue() {
        return new Queue(SUBMISSION_QUEUE_NAME, true);
    }

    @Bean
    public Binding fundTransferSubmissionBinding(Queue fundTransferSubmissionQueue, TopicExchange outboxEventsExchange) {
        return BindingBuilder.bind(fundTransferSubmissionQueue)
                .to(outboxEventsExchange)
                .with(MATCH_ALL_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter("com.example.ledgerpractice");
    }
}
