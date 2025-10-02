package open.vincentf13.exchange.gateway.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Basic health endpoint for the gateway service.
 */
@RestController
class HealthController {

    @GetMapping("/internal/health")
    ResponseEntity<String> health() {
        return ResponseEntity.ok("gateway-ok");
    }
}
