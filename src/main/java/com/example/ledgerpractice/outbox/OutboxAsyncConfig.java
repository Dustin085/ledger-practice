package com.example.ledgerpractice.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 專門給 OutboxRelay 立即觸發用的 executor，跟 Spring Boot 預設的全域 async executor
// 分開，範圍只限於這個用途。佇列滿了故意不用會讓呼叫端變慢的 CallerRunsPolicy——
// 這筆事件直接放棄即可，反正 OutboxRelay 的排程兜底最慢 30 秒內就會把它撿回去重新發布。
@Configuration
@Slf4j
public class OutboxAsyncConfig {

    @Bean
    public TaskExecutor outboxAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("outbox-async-");
        executor.setRejectedExecutionHandler((task, exec) ->
                log.warn("Outbox 立即發布佇列已滿，這筆改交給排程兜底處理"));
        executor.initialize();
        return executor;
    }
}
