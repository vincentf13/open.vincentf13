package open.vincentf13.exchange.user.service;

import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareResponse;
import open.vincentf13.exchange.auth.sdk.rest.api.enums.AuthCredentialType;
import open.vincentf13.exchange.auth.sdk.rest.client.ExchangeAuthClient;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.infra.UserErrorCode;
import open.vincentf13.exchange.user.infra.UserEvent;
import open.vincentf13.exchange.user.infra.persistence.repository.UserRepository;
import open.vincentf13.exchange.user.infra.retry.RetryTaskType;
import open.vincentf13.exchange.user.infra.retry.dto.AuthCredentialCreatePayload;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserRegisterRequest;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserResponse;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import open.vincentf13.sdk.infra.mysql.retry.task.RetryTaskResult;
import open.vincentf13.sdk.infra.mysql.retry.task.RetryTaskService;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskPO;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskRepository;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskStatus;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class UserCommandService {

  private final UserRepository userRepository;
  private final ExchangeAuthClient authClient;
  private final TransactionTemplate transactionTemplate;
  private final RetryTaskRepository retryTaskRepository;
  private final RetryTaskService retryTaskService;

  @Value("${open.vincentf13.exchange.user.auth.retry-delay:PT60S}")
  private Duration retryDelay;

  public UserResponse register(@Valid UserRegisterRequest request) {
    final String normalizedEmail = User.normalizeEmail(request.email());

    AuthCredentialPrepareResponse preparedData =
        OpenApiClientInvoker.call(
            () ->
                authClient.prepare(
                    new AuthCredentialPrepareRequest(
                        AuthCredentialType.PASSWORD, request.password())),
            msg ->
                OpenException.of(
                    UserErrorCode.USER_AUTH_PREPARATION_FAILED,
                    Map.of("email", normalizedEmail, "remoteMessage", msg)));
    if (preparedData.secretHash() == null || preparedData.salt() == null) {
      throw OpenException.of(
          UserErrorCode.USER_AUTH_PREPARATION_FAILED, Map.of("email", normalizedEmail));
    }

    User user = User.createActive(request.email(), request.externalId());
    AuthCredentialCreatePayload payload =
        AuthCredentialCreatePayload.builder()
            .credentialType(AuthCredentialType.PASSWORD)
            .secretHash(preparedData.secretHash())
            .salt(preparedData.salt())
            .build();
    Instant now = Instant.now();
    RetryTaskPO task =
        transactionTemplate.execute(
            status -> {
              userRepository.insertSelective(user);

              payload.setUserId(user.getId());
              return retryTaskRepository.insertPendingTask(
                  RetryTaskType.AUTH_CREDENTIAL_CREATE,
                  user.getId() + ":" + AuthCredentialType.PASSWORD,
                  payload,
                  5,
                  null,
                  now);
            });

    retryTaskService.handleTask(task, retryDelay, retryTask -> createAuthCredential(payload));

    return OpenObjectMapper.convert(user, UserResponse.class);
  }

  public RetryTaskResult<Void> createAuthCredential(AuthCredentialCreatePayload payload) {
    if (payload == null
        || payload.getUserId() == null
        || payload.getCredentialType() == null
        || payload.getSecretHash() == null
        || payload.getSalt() == null) {
      return new RetryTaskResult<>(RetryTaskStatus.FAIL_TERMINAL, "invalidPayload", null);
    }
    AuthCredentialCreateRequest credentialRequest =
        new AuthCredentialCreateRequest(
            payload.getUserId(),
            payload.getCredentialType(),
            payload.getSecretHash(),
            payload.getSalt(),
            "ACTIVE");
    try {
      OpenApiClientInvoker.call(
          () -> authClient.create(credentialRequest),
          msg ->
              new IllegalStateException(
                  "Failed to create auth credential for user %s: %s"
                      .formatted(payload.getUserId(), msg)));
      return new RetryTaskResult<>(RetryTaskStatus.SUCCESS, "OK", null);
    } catch (Exception ex) {
      OpenLog.warn(
          log,
          UserEvent.AUTH_CREDENTIAL_PERSIST_FAILED,
          "userId",
          payload.getUserId(),
          "reason",
          Optional.ofNullable(ex.getMessage()).orElse("UNKNOWN_ERROR"));
      return new RetryTaskResult<>(RetryTaskStatus.PENDING, "notReady", null);
    }
  }
}
