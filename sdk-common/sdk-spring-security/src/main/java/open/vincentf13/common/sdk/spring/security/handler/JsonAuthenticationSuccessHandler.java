package open.vincentf13.common.sdk.spring.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class JsonAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String MESSAGE_KEY = "auth.login.success";

    private final ObjectMapper objectMapper;
    private final MessageSourceAccessor messages;

    public JsonAuthenticationSuccessHandler(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messages = new MessageSourceAccessor(messageSource);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String localizedMessage = messages.getMessage(MESSAGE_KEY, "Login successful");
        OpenApiResponse<Void> body = OpenApiResponse.success().withMeta(Map.of("message", localizedMessage));

        objectMapper.writeValue(response.getWriter(), body);
    }
}
