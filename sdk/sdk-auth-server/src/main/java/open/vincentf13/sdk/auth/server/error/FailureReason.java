package open.vincentf13.sdk.auth.server.error;

import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;

/**
 * Enumerates authentication-related failures so different entry points (login, refresh, etc.)
 * can present consistent error codes/messages.
 */
public enum FailureReason {

    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "AUTH_ACCOUNT_LOCKED", "auth.login.failure.locked", "Account locked"),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "AUTH_ACCOUNT_DISABLED", "auth.login.failure.disabled", "Account disabled or not activated"),
    CREDENTIALS_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_CREDENTIALS_EXPIRED", "auth.login.failure.credentials-expired", "Password expired"),
    ACCOUNT_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_ACCOUNT_EXPIRED", "auth.login.failure.account-expired", "Account expired"),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_BAD_CREDENTIALS", "auth.login.failure.bad-credentials", "Invalid username or password"),
    REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_INVALID", "auth.refresh.invalid", "Refresh jwtToken invalid");

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

    public String messageKey() {
        return messageKey;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public String resolveMessage(MessageSourceAccessor messages) {
        return messages.getMessage(messageKey, defaultMessage);
    }

    public static FailureReason from(AuthenticationException exception) {
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

