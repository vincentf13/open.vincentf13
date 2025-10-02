package open.vincentf13.common.observability.log;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Provides baseline logging conventions (MDC defaults, etc.).
 */
@AutoConfiguration
public class LoggingAutoConfiguration {

    @EventListener(ApplicationReadyEvent.class)
    public void initDefaultContext() {
        MDC.put("app", System.getProperty("spring.application.name", "exchange"));
    }
}
