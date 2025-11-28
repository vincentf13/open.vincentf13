package open.vincentf13.sdk.auth.server.controller;

import jakarta.validation.Valid;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.auth.server.AuthServerEvent;
import open.vincentf13.sdk.auth.server.OpenJwtSessionService;
import open.vincentf13.sdk.auth.server.controller.dto.FailureReason;
import open.vincentf13.sdk.auth.server.controller.dto.JwtTokenPair;
import open.vincentf13.sdk.auth.server.controller.dto.LoginRequest;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
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
@RequestMapping("/api/")
public class AuthLoginController {
    
    private static final String LOGIN_SUCCESS_KEY = "auth.login.success";
    
    private final AuthenticationManager authenticationManager;
    private final OpenJwtSessionService sessionService;
    private final MessageSourceAccessor messages;
    
    public AuthLoginController(AuthenticationManager authenticationManager,
                               OpenJwtSessionService sessionService,
                               MessageSource messageSource) {
        this.authenticationManager = authenticationManager;
        this.sessionService = sessionService;
        this.messages = new MessageSourceAccessor(messageSource);
    }
    
    @PublicAPI
    @PostMapping("/login")
    public ResponseEntity<OpenApiResponse<JwtTokenPair>> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            OpenJwtSessionService.IssueResult tokens = sessionService.issue(authentication);
            JwtTokenPair payload = new JwtTokenPair(tokens.accessToken().token(),
                                                    tokens.accessToken().issuedAt(),
                                                    tokens.accessToken().expiresAt(),
                                                    tokens.refreshToken().token(),
                                                    tokens.refreshToken().expiresAt(),
                                                    tokens.sessionId());
            
            OpenLog.info(AuthServerEvent.LOGIN_SUCCESS,
                         "username", authentication.getName());
            
            OpenApiResponse<JwtTokenPair> body = OpenApiResponse.success(payload)
                                                                .withMeta(Map.of("message", messages.getMessage(LOGIN_SUCCESS_KEY, "Login successful")));
            return ResponseEntity.ok(body);
        } catch (AuthenticationException ex) {
            FailureReason reason = FailureReason.from(ex);
            String username = request.email() != null ? request.email() : "<unknown>";
            OpenLog.warn(AuthServerEvent.LOGIN_FAILURE,
                         ex,
                         "username", username,
                         "code", reason.code());
            OpenApiResponse<JwtTokenPair> body = OpenApiResponse.failure(reason.code(), reason.resolveMessage(messages));
            return ResponseEntity.status(reason.status()).body(body);
        }
    }
}
