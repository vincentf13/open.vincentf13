package open.vincentf13.common.sdk.spring.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import open.vincentf13.common.core.log.OpenLog;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class LoginFailureHandler implements org.springframework.security.web.authentication.AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);

    private final ObjectMapper objectMapper;
    private final MessageSourceAccessor messages;

    public LoginFailureHandler(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messages = new MessageSourceAccessor(messageSource);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        FailureReason reason = FailureReason.from(exception);
        String localizedMessage = reason.resolveMessage(messages);
        OpenApiResponse<Void> body = OpenApiResponse.failure(reason.code(), localizedMessage);

        String username = request != null ? request.getParameter("username") : null;
        OpenLog.warn(log,
                     "LoginFailure",
                     "Authentication failed",
                     exception,
                     "username", username != null && !username.isBlank() ? username : "<unknown>",
                     "code", reason.code(),
                     "remote", request != null ? request.getRemoteAddr() : "<unknown>");

        objectMapper.writeValue(response.getWriter(), body);
    }

    public enum FailureReason {
        ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "AUTH_ACCOUNT_LOCKED", "auth.login.failure.locked", "Account locked"),
        ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "AUTH_ACCOUNT_DISABLED", "auth.login.failure.disabled", "Account disabled or not activated"),
        CREDENTIALS_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_CREDENTIALS_EXPIRED", "auth.login.failure.credentials-expired", "Password expired"),
        ACCOUNT_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_ACCOUNT_EXPIRED", "auth.login.failure.account-expired", "Account expired"),
        BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_BAD_CREDENTIALS", "auth.login.failure.bad-credentials", "Invalid username or password"),
        REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_INVALID", "auth.refresh.invalid", "Refresh token invalid");

        private final HttpStatus status;
        private final String code;
        private final String messageKey;
        private final String defaultMessage;

        FailureReason(HttpStatus status, String code, String messageKey, String defaultMessage) {
            this.status = status;
            this.code = code;
            this.messageKey = messageKey;
            this.defaultMessage = defaultMessage;
        }

        public HttpStatus status() {
            return status;
        }

        public String code() {
            return code;
        }

        public String resolveMessage(MessageSourceAccessor messages) {
            return messages.getMessage(messageKey, defaultMessage);
        }

        public String defaultMessage() {
            return defaultMessage;
        }

        public String messageKey() {
            return messageKey;
        }

        private static FailureReason from(AuthenticationException exception) {
            if (exception instanceof LockedException) {
                return ACCOUNT_LOCKED;
            }
            if (exception instanceof DisabledException) {
                return ACCOUNT_DISABLED;
            }
            if (exception instanceof CredentialsExpiredException) {
                return CREDENTIALS_EXPIRED;
            }
            if (exception instanceof AccountExpiredException) {
                return ACCOUNT_EXPIRED;
            }
            return BAD_CREDENTIALS;
        }
    }
}
