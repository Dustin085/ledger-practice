package com.example.ledgerpractice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LedgerPracticeApplicationTests {

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Test
    void contextLoads() {
    }

    @Test
    void schedulerHasMoreThanOneThreadSoSlowTasksDoNotBlockEachOther() {
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isGreaterThan(1);
    }

}
