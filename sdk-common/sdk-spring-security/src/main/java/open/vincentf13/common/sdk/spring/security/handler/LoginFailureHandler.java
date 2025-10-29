package open.vincentf13.common.sdk.spring.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import open.vincentf13.common.core.log.OpenLog;
import open.vincentf13.common.sdk.spring.security.error.FailureReason;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
}
