package com.example.exchange.positions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.exchange")
public class PositionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PositionsApplication.class, args);
    }
}
