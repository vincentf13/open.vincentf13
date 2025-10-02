package open.vincentf13.riskmargin.interfaces;

import open.vincentf13.common.open.exchange.riskmargin.interfaces.RiskCheckRequestDto;
import open.vincentf13.riskmargin.app.RiskCheckService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST facade exposing synchronous risk checks.
 */
@RestController
@RequestMapping("/api/risk-checks")
public class RiskCheckController {

    private final RiskCheckService service;

    public RiskCheckController(RiskCheckService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Boolean> validate(@RequestBody RiskCheckRequestDto request) {
        return ResponseEntity.ok(service.validate(request));
    }
}
