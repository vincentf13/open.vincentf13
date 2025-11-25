package open.vincentf13.exchange.user.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareResponse;
import open.vincentf13.exchange.auth.sdk.rest.api.enums.AuthCredentialType;
import open.vincentf13.exchange.auth.sdk.rest.client.ExchangeAuthClient;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserRegisterRequest;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserResponse;
import open.vincentf13.exchange.user.domain.model.AuthCredentialPending;
import open.vincentf13.exchange.user.sdk.rest.api.enums.AuthCredentialPendingStatus;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.infra.UserErrorCode;
import open.vincentf13.exchange.user.infra.UserEvent;
import open.vincentf13.exchange.user.infra.persistence.repository.AuthCredentialPendingRepository;
import open.vincentf13.exchange.user.infra.persistence.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Validated
public class UserCommandService {

    private final UserRepository userRepository;
    private final ExchangeAuthClient authClient;
    private final AuthCredentialPendingRepository authCredentialPendingRepository;
    private final TransactionTemplate transactionTemplate;

    public UserResponse register(@Valid UserRegisterRequest request)  {
        final String normalizedEmail = User.normalizeEmail(request.email());

        AuthCredentialPrepareResponse preparedData = OpenApiClientInvoker.call(
                () -> authClient.prepare(new AuthCredentialPrepareRequest(AuthCredentialType.PASSWORD, request.password())),
                msg -> OpenException.of(UserErrorCode.USER_AUTH_PREPARATION_FAILED,
                                        Map.of("email", normalizedEmail, "remoteMessage", msg))
        );
        if (preparedData.secretHash() == null || preparedData.salt() == null) {
            throw OpenException.of(UserErrorCode.USER_AUTH_PREPARATION_FAILED,
                                    Map.of("email", normalizedEmail));
        }

        RegistrationContext context = transactionTemplate.execute(status -> {
            User user = User.createActive(request.email(), request.externalId());
            userRepository.insertSelective(user);

            Instant now = Instant.now();
            AuthCredentialPending pendingCredential = AuthCredentialPending.builder()
                    .userId(user.getId())
                    .credentialType(AuthCredentialType.PASSWORD)
                    .secretHash(preparedData.secretHash())
                    .salt(preparedData.salt())
                    .status(AuthCredentialPendingStatus.PENDING)
                    .retryCount(0)
                    .nextRetryAt(null)
                    .lastError(null)
                    .build();
            authCredentialPendingRepository.insert(pendingCredential);
            return new RegistrationContext(user, pendingCredential);
        });

        if (context == null) {
            throw new IllegalStateException("Registration transaction returned no context");
        }

        User persistedUser = context.user();
        AuthCredentialPending pendingCredential = context.pending();

        AuthCredentialCreateRequest credentialRequest = new AuthCredentialCreateRequest(
                persistedUser.getId(),
                AuthCredentialType.PASSWORD,
                pendingCredential.getSecretHash(),
                pendingCredential.getSalt(),
                "ACTIVE"
        );

        try {
            OpenApiClientInvoker.call(
                    () -> authClient.create(credentialRequest),
                    msg -> new IllegalStateException(
                            "Failed to create auth credential for user %s: %s".formatted(persistedUser.getId(), msg))
            );
            authCredentialPendingRepository.markCompleted(persistedUser.getId(), AuthCredentialType.PASSWORD, Instant.now());
        } catch (Exception ex) {
            handleCredentialFailure(persistedUser.getId(), ex.getMessage());
        }

        return OpenObjectMapper.convert(persistedUser, UserResponse.class);
    }

    private void handleCredentialFailure(Long userId, String reason) {
        String message = Optional.ofNullable(reason).orElse("UNKNOWN_ERROR");
        OpenLog.warn(UserEvent.AUTH_CREDENTIAL_PERSIST_FAILED,
                "userId", userId,
                "reason", message);
        authCredentialPendingRepository.markFailure(
                userId,
                AuthCredentialType.PASSWORD,
                message,
                Instant.now().plusSeconds(60),
                AuthCredentialPendingStatus.PENDING
        );
    }

    private record RegistrationContext(User user, AuthCredentialPending pending) { }
}
