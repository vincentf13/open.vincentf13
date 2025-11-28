package open.vincentf13.exchange.user.infra.scheduler;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.user.infra.UserEvent;
import open.vincentf13.exchange.user.service.AuthCredentialPendingRetryService;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCredentialPendingScheduler {
    
    private final AuthCredentialPendingRetryService retryService;
    
    @Scheduled(fixedDelayString = "${exchange.user.auth.retry.fixedDelay:600000}")
    public void retryPendingCredentials() {
        try {
            retryService.processPendingCredentials();
        } catch (Exception ex) {
            OpenLog.error(UserEvent.AUTH_CREDENTIAL_RETRY_JOB_FAILED, ex);
        }
    }
}
