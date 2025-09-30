package com.example.exchange.liquidation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.exchange")
public class LiquidationApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiquidationApplication.class, args);
    }
}
