package open.vincentf13.exchange.account.ledger;

import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = OpenConstant.Package.Names.BASE_PACKAGE)
public class LedgerAccountApp {
    
    public static void main(String[] args) {
        SpringApplication.run(LedgerAccountApp.class, args);
    }
}
