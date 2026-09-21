package com.example.ledgerpractice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LedgerPracticeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerPracticeApplication.class, args);
    }

}
