package open.vincentf13.sdk.auth.server.controller;

import jakarta.validation.Valid;
import open.vincentf13.sdk.auth.jwt.token.JwtResponse;
import open.vincentf13.sdk.auth.server.controller.dto.LoginRequest;
import open.vincentf13.sdk.auth.server.error.FailureReason;
import open.vincentf13.sdk.auth.server.service.AuthJwtSessionService;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthLoginController {

    private static final Logger log = LoggerFactory.getLogger(AuthLoginController.class);
    private static final String LOGIN_SUCCESS_KEY = "auth.login.success";

    private final AuthenticationManager authenticationManager;
    private final AuthJwtSessionService sessionService;
    private final MessageSourceAccessor messages;

    public AuthLoginController(AuthenticationManager authenticationManager,
                               AuthJwtSessionService sessionService,
                               MessageSource messageSource) {
        this.authenticationManager = authenticationManager;
        this.sessionService = sessionService;
        this.messages = new MessageSourceAccessor(messageSource);
    }

    @PostMapping("/login")
    public ResponseEntity<OpenApiResponse<JwtResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            AuthJwtSessionService.IssueResult tokens = sessionService.issue(authentication);
            JwtResponse payload = new JwtResponse(tokens.accessToken().token(),
                    tokens.accessToken().issuedAt(),
                    tokens.accessToken().expiresAt(),
                    tokens.refreshToken().token(),
                    tokens.refreshToken().expiresAt(),
                    tokens.sessionId());

            OpenLog.info(log,
                    "LoginSuccess",
                    "User authenticated",
                    "username", authentication.getName());

            OpenApiResponse<JwtResponse> body = OpenApiResponse.success(payload)
                    .withMeta(Map.of("message", messages.getMessage(LOGIN_SUCCESS_KEY, "Login successful")));
            return ResponseEntity.ok(body);
        } catch (AuthenticationException ex) {
            FailureReason reason = FailureReason.from(ex);
            String username = request.email() != null ? request.email() : "<unknown>";
            OpenLog.warn(log,
                    "LoginFailure",
                    "Authentication failed",
                    ex,
                    "username", username,
                    "code", reason.code());
            OpenApiResponse<JwtResponse> body = OpenApiResponse.failure(reason.code(), reason.resolveMessage(messages));
            return ResponseEntity.status(reason.status()).body(body);
        }
    }
}
