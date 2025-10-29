package open.vincentf13.common.sdk.spring.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import open.vincentf13.common.core.log.OpenLog;
import open.vincentf13.common.infra.jwt.session.JwtSessionService;
import open.vincentf13.common.infra.jwt.token.JwtResponse;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class LoginSuccessHandler implements org.springframework.security.web.authentication.AuthenticationSuccessHandler {

    private static final String MESSAGE_KEY = "auth.login.success";
    private static final Logger log = LoggerFactory.getLogger(LoginSuccessHandler.class);

    private final ObjectMapper objectMapper;
    private final MessageSourceAccessor messages;
    private final JwtSessionService jwtSessionService;

    public LoginSuccessHandler(ObjectMapper objectMapper,
                               MessageSource messageSource,
                               JwtSessionService jwtSessionService) {
        this.objectMapper = objectMapper;
        this.messages = new MessageSourceAccessor(messageSource);
        this.jwtSessionService = jwtSessionService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String localizedMessage = messages.getMessage(MESSAGE_KEY, "Login successful");

        JwtSessionService.IssueResult tokens = jwtSessionService.issue(authentication);
        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken().token());
        JwtResponse payload = new JwtResponse(tokens.accessToken().token(),
                                             tokens.accessToken().issuedAt(),
                                             tokens.accessToken().expiresAt(),
                                             tokens.refreshToken().token(),
                                             tokens.refreshToken().expiresAt(),
                                             tokens.sessionId());

        OpenApiResponse<JwtResponse> body = OpenApiResponse.success(payload)
                .withMeta(Map.of("message", localizedMessage));

        OpenLog.info(log,
                     "LoginSuccess",
                     "User authenticated",
                     "username", authentication != null ? authentication.getName() : "<unknown>",
                     "remote", request != null ? request.getRemoteAddr() : "<unknown>");

        objectMapper.writeValue(response.getWriter(), body);
    }
}
