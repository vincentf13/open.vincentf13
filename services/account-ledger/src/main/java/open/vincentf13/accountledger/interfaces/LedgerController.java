package open.vincentf13.accountledger.interfaces;

import open.vincentf13.accountledger.app.LedgerService;
import open.vincentf13.api.account.AccountBalanceDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Facade exposing ledger read APIs.
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/accounts/{accountId}/assets/{asset}")
    public ResponseEntity<AccountBalanceDto> getBalance(@PathVariable String accountId,
                                                         @PathVariable String asset) {
        return ResponseEntity.ok(ledgerService.findBalance(accountId, asset));
    }
}
