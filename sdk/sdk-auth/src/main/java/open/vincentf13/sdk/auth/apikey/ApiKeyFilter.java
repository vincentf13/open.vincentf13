package open.vincentf13.sdk.auth.apikey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import open.vincentf13.sdk.auth.auth.AuthAttributes;
import open.vincentf13.sdk.auth.auth.AuthType;
import open.vincentf13.sdk.auth.auth.PrivateAPI;
import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.EnumSet;

public class ApiKeyFilter extends OncePerRequestFilter {
    
    private final ApiKeyValidator apiKeyValidator;
    private final RequestMappingHandlerMapping handlerMapping;
    
    public ApiKeyFilter(ApiKeyValidator apiKeyValidator,
                        RequestMappingHandlerMapping handlerMapping) {
        this.apiKeyValidator = apiKeyValidator;
        this.handlerMapping = handlerMapping;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        
        HandlerMethod handlerMethod;
        try {
            // Note: This might not work perfectly with WebFlux, but is fine for MVC.
            // A more robust solution might involve inspecting the request attributes set by the dispatcher servlet.
            Object handler = handlerMapping.getHandler(request);
            if (handler instanceof HandlerExecutionChain executionChain) {
                handler = executionChain.getHandler();
            }
            if (!(handler instanceof HandlerMethod)) {
                filterChain.doFilter(request, response);
                return;
            }
            handlerMethod = (HandlerMethod) handler;
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }
        
        PrivateAPI privateApiAnnotation = findPrivateApiAnnotation(handlerMethod);
        
        if (privateApiAnnotation != null) {
            String apiKey = request.getHeader(OpenConstant.HttpHeader.API_KEY.value());
            if (apiKey == null || !apiKeyValidator.isValid(apiKey)) {
                filterChain.doFilter(request, response);
                return;
            }
            markAuthenticatedWithApiKey(request);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private void markAuthenticatedWithApiKey(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthAttributes.AUTH_TYPES);
        EnumSet<AuthType> types;
        if (attr instanceof EnumSet<?>) {
            //noinspection unchecked
            types = (EnumSet<AuthType>) attr;
        } else {
            types = EnumSet.noneOf(AuthType.class);
        }
        types.add(AuthType.API_KEY);
        request.setAttribute(AuthAttributes.AUTH_TYPES, types);
    }
    
    private PrivateAPI findPrivateApiAnnotation(HandlerMethod handlerMethod) {
        PrivateAPI annotation = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), PrivateAPI.class);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), PrivateAPI.class);
        }
        return annotation;
    }
}
