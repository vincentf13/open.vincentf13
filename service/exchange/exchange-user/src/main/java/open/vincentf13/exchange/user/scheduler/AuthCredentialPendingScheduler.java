package open.vincentf13.exchange.user.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.user.service.AuthCredentialPendingRetryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuthCredentialPendingScheduler {

    private final AuthCredentialPendingRetryService retryService;

    @Scheduled(fixedDelayString = "${exchange.user.auth.retry.fixedDelay:60000}")
    public void retryPendingCredentials() {
        try {
            retryService.processPendingCredentials();
        } catch (Exception ex) {
            log.error("Unexpected error during pending auth credential retry job", ex);
        }
    }
}
