package com.raglaw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.raglaw")
public class RagLawApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagLawApplication.class, args);
    }
}
