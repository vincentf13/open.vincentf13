package open.vincentf13.accountledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "open.vincentf13")
public class AccountLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountLedgerApplication.class, args);
    }
}
