package com.raglaw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.raglaw")
@EnableScheduling
public class RagLawApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagLawApplication.class, args);
    }
}
