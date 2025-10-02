package open.vincentf13.positions.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Consumes account ledger events to maintain position projections.
 */
@Service
public class PositionProjectionService {

    private static final Logger log = LoggerFactory.getLogger(PositionProjectionService.class);

    public void apply(String event) {
        log.debug("Applying event {}", event);
    }
}
