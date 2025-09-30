package com.example.exchange.accountledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.exchange")
public class AccountLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountLedgerApplication.class, args);
    }
}
