
package open.vincentf13.sdk.auth.server.controller;

import open.vincentf13.sdk.auth.server.error.FailureReason;
import open.vincentf13.sdk.auth.jwt.token.JwtResponse;
import open.vincentf13.sdk.auth.jwt.token.model.JwtAuthenticationToken;
import open.vincentf13.sdk.auth.server.service.AuthJwtSessionService;
import open.vincentf13.sdk.auth.server.service.AuthJwtSessionService.IssueResult;
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
@RequestMapping("/api/auth")
public class SessionController {

    private final AuthJwtSessionService sessionService;
    private final MessageSourceAccessor messages;

    public SessionController(AuthJwtSessionService sessionService,
                            MessageSource messageSource) {
        this.sessionService = sessionService;
        this.messages = new MessageSourceAccessor(messageSource);
    }

    @PostMapping("/refresh")
    public ResponseEntity<OpenApiResponse<JwtResponse>> refresh(@RequestBody RefreshTokenPayload payload) {
        FailureReason failure = FailureReason.REFRESH_INVALID;
        return sessionService.refresh(payload.refreshToken())
                .map(SessionController::toResponse)
                .orElseGet(() -> ResponseEntity.status(failure.status())
                        .body(OpenApiResponse.<JwtResponse>failure(failure.code(), failure.resolveMessage(messages))));
    }

    @PostMapping("/logout")
    public ResponseEntity<OpenApiResponse<Void>> logout(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth && jwtAuth.hasSessionId()) {
            sessionService.revoke(jwtAuth.getSessionId(), "logout");
        }
        return ResponseEntity.ok(OpenApiResponse.success(null));
    }

    private static ResponseEntity<OpenApiResponse<JwtResponse>> toResponse(IssueResult result) {
        JwtResponse payload = new JwtResponse(result.accessToken().token(),
                                              result.accessToken().issuedAt(),
                                              result.accessToken().expiresAt(),
                                              result.refreshToken().token(),
                                              result.refreshToken().expiresAt(),
                                              result.sessionId());
        return ResponseEntity.ok(OpenApiResponse.success(payload));
    }

    public record RefreshTokenPayload(String refreshToken) { }
}
