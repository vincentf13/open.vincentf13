package open.vincentf13.sdk.auth.server.controller;

import open.vincentf13.sdk.auth.auth.Jwt;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.auth.jwt.token.JwtToken;
import open.vincentf13.sdk.auth.server.OpenJwtSessionService;
import open.vincentf13.sdk.auth.server.OpenJwtSessionService.IssueResult;
import open.vincentf13.sdk.auth.server.controller.dto.FailureReason;
import open.vincentf13.sdk.auth.server.controller.dto.JwtTokenPair;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 範例控制器：展示如何在 AUTH 服務提供刷新 / 登出 API。
@RestController
@RequestMapping("/api/")
public class SessionController {

  private final OpenJwtSessionService sessionService;
  private final MessageSourceAccessor messages;

  public SessionController(OpenJwtSessionService sessionService, MessageSource messageSource) {
    this.sessionService = sessionService;
    this.messages = new MessageSourceAccessor(messageSource);
  }

  private static ResponseEntity<OpenApiResponse<JwtTokenPair>> toResponse(IssueResult result) {
    JwtTokenPair payload =
        new JwtTokenPair(
            result.accessToken().token(),
            result.accessToken().issuedAt(),
            result.accessToken().expiresAt(),
            result.refreshToken().token(),
            result.refreshToken().expiresAt(),
            result.sessionId());
    return ResponseEntity.ok(OpenApiResponse.success(payload));
  }

  @PostMapping("/refresh")
  @PublicAPI
  public ResponseEntity<OpenApiResponse<JwtTokenPair>> refresh(
      @RequestBody RefreshTokenPayload payload) {
    FailureReason failure = FailureReason.REFRESH_INVALID;
    return sessionService
        .refresh(payload.refreshToken())
        .map(SessionController::toResponse)
        .orElseGet(
            () ->
                ResponseEntity.status(failure.status())
                    .body(
                        OpenApiResponse.<JwtTokenPair>failure(
                            failure.code(), failure.resolveMessage(messages))));
  }

  @PostMapping("/logout")
  @Jwt
  public ResponseEntity<OpenApiResponse<Void>> logout(Authentication authentication) {
    if (authentication instanceof JwtToken jwtAuth && jwtAuth.hasSessionId()) {
      sessionService.revoke(jwtAuth.getSessionId(), "logout");
    }
    return ResponseEntity.ok(OpenApiResponse.success(null));
  }

  public record RefreshTokenPayload(String refreshToken) {}
}
