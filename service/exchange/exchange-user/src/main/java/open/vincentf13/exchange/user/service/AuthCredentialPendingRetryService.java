package open.vincentf13.exchange.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.sdk.rest.client.ExchangeAuthClient;
import open.vincentf13.exchange.user.domain.model.AuthCredentialPending;
import open.vincentf13.exchange.user.infra.UserEvent;
import open.vincentf13.exchange.user.infra.persistence.po.AuthCredentialPendingPO;
import open.vincentf13.exchange.user.infra.persistence.repository.AuthCredentialPendingRepository;
import open.vincentf13.exchange.user.sdk.rest.api.enums.AuthCredentialPendingStatus;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthCredentialPendingRetryService {
    
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(60);
    
    private final AuthCredentialPendingRepository authCredentialPendingRepository;
    private final ExchangeAuthClient authClient;
    
    public void processPendingCredentials() {
        LambdaQueryWrapper<AuthCredentialPendingPO> wrapper = Wrappers.<AuthCredentialPendingPO>lambdaQuery()
                                                                      .eq(AuthCredentialPendingPO::getStatus, AuthCredentialPendingStatus.PENDING)
                                                                      .and(w -> w.isNull(AuthCredentialPendingPO::getNextRetryAt)
                                                                                 .or()
                                                                                 .le(AuthCredentialPendingPO::getNextRetryAt, Instant.now()))
                                                                      .orderByAsc(AuthCredentialPendingPO::getUpdatedAt)
                                                                      .last("LIMIT " + DEFAULT_BATCH_SIZE);
        List<AuthCredentialPending> pendings = authCredentialPendingRepository.findBy(wrapper);
        if (pendings.isEmpty()) {
            return;
        }
        
        for (AuthCredentialPending pending : pendings) {
            try {
                handlePendingCredential(pending);
            } catch (Exception ex) {
                OpenLog.warn(UserEvent.AUTH_CREDENTIAL_RETRY_ERROR, ex,
                             "userId", pending.getUserId(),
                             "reason", ex.getMessage());
                scheduleNextRetry(pending, ex.getMessage());
            }
        }
    }
    
    private void handlePendingCredential(AuthCredentialPending pending) {
        AuthCredentialCreateRequest request = new AuthCredentialCreateRequest(
                pending.getUserId(),
                pending.getCredentialType(),
                pending.getSecretHash(),
                pending.getSalt(),
                "ACTIVE"
        );
        
        try {
            OpenApiClientInvoker.call(
                    () -> authClient.create(request),
                    msg -> new IllegalStateException(
                            "Failed to create credential for user %s during retry: %s".formatted(pending.getUserId(), msg))
                                     );
            authCredentialPendingRepository.update(AuthCredentialPending.builder()
                                                                        .status(AuthCredentialPendingStatus.COMPLETED)
                                                                        .retryCount(0)
                                                                        .nextRetryAt(null)
                                                                        .lastError(null)
                                                                        .build(),
                                                   Wrappers.<AuthCredentialPendingPO>lambdaUpdate()
                                                           .eq(AuthCredentialPendingPO::getUserId, pending.getUserId())
                                                           .eq(AuthCredentialPendingPO::getCredentialType, pending.getCredentialType()));
            OpenLog.info(UserEvent.AUTH_CREDENTIAL_RETRY_SUCCESS,
                         "userId", pending.getUserId());
        } catch (Exception ex) {
            scheduleNextRetry(pending, ex.getMessage());
        }
    }
    
    private void scheduleNextRetry(AuthCredentialPending pending,
                                   String reason) {
        int currentRetry = Objects.requireNonNullElse(pending.getRetryCount(), 0);
        int nextRetry = currentRetry + 1;
        boolean exceeded = nextRetry >= MAX_RETRY_ATTEMPTS;
        AuthCredentialPendingStatus nextStatus = exceeded ? AuthCredentialPendingStatus.FAILED : AuthCredentialPendingStatus.PENDING;
        
        Instant nextRetryAt = exceeded ? null : Instant.now().plus(calculateDelay(nextRetry));
        String sanitizedReason = sanitizeReason(reason);
        
        authCredentialPendingRepository.update(AuthCredentialPending.builder()
                                                                    .status(nextStatus)
                                                                    .lastError(sanitizedReason)
                                                                    .nextRetryAt(nextRetryAt)
                                                                    .build(),
                                               Wrappers.<AuthCredentialPendingPO>lambdaUpdate()
                                                       .eq(AuthCredentialPendingPO::getUserId, pending.getUserId())
                                                       .eq(AuthCredentialPendingPO::getCredentialType, pending.getCredentialType())
                                                       .setSql("retry_count = retry_count + 1"));
        
        if (exceeded) {
            OpenLog.error(UserEvent.AUTH_CREDENTIAL_RETRY_EXCEEDED, null,
                          "userId", pending.getUserId(),
                          "credentialType", pending.getCredentialType());
        } else {
            OpenLog.info(UserEvent.AUTH_CREDENTIAL_RETRY_SCHEDULED,
                         "retry", nextRetry,
                         "userId", pending.getUserId(),
                         "credentialType", pending.getCredentialType(),
                         "nextRetryAt", nextRetryAt);
        }
    }
    
    private Duration calculateDelay(int retryAttempt) {
        long multiplier = Math.max(1L, retryAttempt);
        return BASE_RETRY_DELAY.multipliedBy(multiplier);
    }
    
    private String sanitizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        if (reason.length() <= 512) {
            return reason;
        }
        return reason.substring(0, 512);
    }
}
