package open.vincentf13.exchange.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.sdk.core.OpenMapstruct;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareResponse;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialResponse;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialType;
import open.vincentf13.exchange.auth.sdk.rest.client.ExchangeAuthClient;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserRegisterRequest;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserResponse;
import open.vincentf13.exchange.user.domain.model.AuthCredentialPending;
import open.vincentf13.exchange.user.domain.model.AuthCredentialPendingStatus;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.domain.model.UserErrorCode;
import open.vincentf13.exchange.user.domain.service.UserDomainService;
import open.vincentf13.exchange.user.infra.persistence.repository.AuthCredentialPendingRepository;
import open.vincentf13.exchange.user.infra.persistence.repository.UserRepository;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserDomainService userDomainService;
    private final ExchangeAuthClient authClient;
    private final AuthCredentialPendingRepository authCredentialPendingRepository;
    private final TransactionTemplate transactionTemplate;

    public UserResponse register(UserRegisterRequest request)  {
        final String normalizedEmail = userDomainService.normalizeEmail(request.email());

        OpenApiResponse<AuthCredentialPrepareResponse> prepareResponse = authClient.prepare(
                new AuthCredentialPrepareRequest(AuthCredentialType.PASSWORD, request.password())
        );

        AuthCredentialPrepareResponse preparedData = prepareResponse.data();
        if (!prepareResponse.isSuccess()
                || preparedData == null
                || preparedData.secretHash() == null
                || preparedData.salt() == null) {
            throw OpenServiceException.of(UserErrorCode.USER_AUTH_PREPARATION_FAILED,
                    "Failed to prepare credential secret for email " + normalizedEmail);
        }

        RegistrationContext context = transactionTemplate.execute(status -> {
            User user = userDomainService.createActiveUser(request.email(), request.externalId());
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
                    .createdAt(now)
                    .updatedAt(now)
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
            OpenApiResponse<AuthCredentialResponse> credentialResponse = authClient.create(credentialRequest);
            if (credentialResponse.isSuccess()) {
                authCredentialPendingRepository.markCompleted(persistedUser.getId(), AuthCredentialType.PASSWORD, Instant.now());
            } else {
                handleCredentialFailure(persistedUser.getId(), credentialResponse.message());
            }
        } catch (Exception ex) {
            handleCredentialFailure(persistedUser.getId(), ex.getMessage());
        }

        return OpenMapstruct.map(persistedUser, UserResponse.class);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Long userId = OpenJwtLoginUserInfo.currentUserIdOrThrow(() ->
                OpenServiceException.of(UserErrorCode.USER_NOT_FOUND, "No authenticated user in context"));
        return userRepository.findOne(User.builder().id(userId).build())
                .map(user -> OpenMapstruct.map(user, UserResponse.class))
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found. id=" + userId));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        String normalizedEmail = userDomainService.normalizeEmail(email);
        return userRepository.findOne(User.builder().email(normalizedEmail).build())
                .map(user -> OpenMapstruct.map(user, UserResponse.class))
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found. email=" + normalizedEmail));
    }

    private void handleCredentialFailure(Long userId, String reason) {
        String message = Optional.ofNullable(reason).orElse("UNKNOWN_ERROR");
        log.warn("Failed to persist auth credential for user {}: {}", userId, message);
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
