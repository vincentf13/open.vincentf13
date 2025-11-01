package open.vincentf13.sdk.auth.apikey.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import open.vincentf13.sdk.auth.apikey.key.ApiKeyValidator;
import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyValidator apiKeyValidator;
    private final RequestMappingHandlerMapping handlerMapping;

    public ApiKeyAuthenticationFilter(ApiKeyValidator apiKeyValidator, RequestMappingHandlerMapping handlerMapping) {
        this.apiKeyValidator = apiKeyValidator;
        this.handlerMapping = handlerMapping;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HandlerMethod handlerMethod;
        try {
            // Note: This might not work perfectly with WebFlux, but is fine for MVC.
            // A more robust solution might involve inspecting the request attributes set by the dispatcher servlet.
            Object handler = handlerMapping.getHandler(request);
            if (!(handler instanceof HandlerMethod)) {
                filterChain.doFilter(request, response);
                return;
            }
            handlerMethod = (HandlerMethod) handler;
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        main.java.open.vincentf13.sdk.auth.apikey.annotation.PrivateAPI privateApiAnnotation = findPrivateApiAnnotation(handlerMethod);

        if (privateApiAnnotation != null) {
            String apiKey = request.getHeader(OpenConstant.API_KEY_HEADER);
            if (apiKey == null || !apiKeyValidator.isValid(apiKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid or missing API Key");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private main.java.open.vincentf13.sdk.auth.apikey.annotation.PrivateAPI findPrivateApiAnnotation(HandlerMethod handlerMethod) {
        main.java.open.vincentf13.sdk.auth.apikey.annotation.PrivateAPI annotation = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), main.java.open.vincentf13.sdk.auth.apikey.annotation.PrivateAPI.class);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), main.java.open.vincentf13.sdk.auth.apikey.annotation.PrivateAPI.class);
        }
        return annotation;
    }
}
