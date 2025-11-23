package open.vincentf13.exchange.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.enums.AuthCredentialType;
import open.vincentf13.exchange.auth.sdk.rest.client.ExchangeAuthClient;
import open.vincentf13.exchange.user.domain.model.AuthCredentialPending;
import open.vincentf13.exchange.user.sdk.rest.api.enums.AuthCredentialPendingStatus;
import open.vincentf13.exchange.user.infra.persistence.repository.AuthCredentialPendingRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthCredentialPendingRetryService {

    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(60);

    private final AuthCredentialPendingRepository authCredentialPendingRepository;
    private final ExchangeAuthClient authClient;

    public void processPendingCredentials() {
        List<AuthCredentialPending> pendings = authCredentialPendingRepository.findReady(DEFAULT_BATCH_SIZE, Instant.now());
        if (pendings.isEmpty()) {
            return;
        }

        for (AuthCredentialPending pending : pendings) {
            try {
                handlePendingCredential(pending);
            } catch (Exception ex) {
                log.warn("Unexpected error while retrying credential creation for user {}: {}",
                        pending.getUserId(), ex.getMessage(), ex);
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
            authCredentialPendingRepository.markCompleted(pending.getUserId(), pending.getCredentialType(), Instant.now());
            log.info("Successfully synchronized auth credential for user {}", pending.getUserId());
        } catch (Exception ex) {
            scheduleNextRetry(pending, ex.getMessage());
        }
    }

    private void scheduleNextRetry(AuthCredentialPending pending, String reason) {
        int currentRetry = Objects.requireNonNullElse(pending.getRetryCount(), 0);
        int nextRetry = currentRetry + 1;
        boolean exceeded = nextRetry >= MAX_RETRY_ATTEMPTS;
        AuthCredentialPendingStatus nextStatus = exceeded ? AuthCredentialPendingStatus.FAILED : AuthCredentialPendingStatus.PENDING;

        Instant nextRetryAt = exceeded ? null : Instant.now().plus(calculateDelay(nextRetry));
        String sanitizedReason = sanitizeReason(reason);

        authCredentialPendingRepository.markFailure(
                pending.getUserId(),
                pending.getCredentialType(),
                sanitizedReason,
                nextRetryAt,
                nextStatus
        );

        if (exceeded) {
            log.error("Exceeded retry attempts for user {} credential type {}. marking as FAILED",
                    pending.getUserId(), pending.getCredentialType());
        } else {
            log.info("Scheduled retry {} for user {} credential type {} at {}",
                    nextRetry, pending.getUserId(), pending.getCredentialType(), nextRetryAt);
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
