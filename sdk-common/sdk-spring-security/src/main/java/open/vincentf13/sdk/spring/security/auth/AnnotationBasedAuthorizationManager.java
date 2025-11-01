package open.vincentf13.sdk.spring.security.auth;

import jakarta.servlet.http.HttpServletRequest;
import open.vincentf13.sdk.auth.apikey.annotation.PublicAPI;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.function.Supplier;

public class AnnotationBasedAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final RequestMappingHandlerMapping handlerMapping;

    public AnnotationBasedAuthorizationManager(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext object) {
        HttpServletRequest request = object.getRequest();

        HandlerMethod handlerMethod;
        try {
            Object handler = handlerMapping.getHandler(request);
            if (!(handler instanceof HandlerMethod)) {
                // 非 HandlerMethod（例如靜態資源）預設視為已通過認證
                return new AuthorizationDecision(isAuthenticated(authentication.get()));
            }
            handlerMethod = (HandlerMethod) handler;
        } catch (Exception e) {
            // 找不到對應的 Handler 時預設要求認證
            return new AuthorizationDecision(isAuthenticated(authentication.get()));
        }

        // 1. 檢查是否標註 @PublicAPI，代表不需登入即可存取
        if (hasAnnotation(handlerMethod, PublicAPI.class)) {
            return new AuthorizationDecision(true);
        }

        // 2. 其餘端點（含標註 @PrivateAPI）交由前置 Filter 判斷，走到這裡代表先前已驗證或為匿名請求，只需檢查 Authentication 是否有效
        return new AuthorizationDecision(isAuthenticated(authentication.get()));
    }

    private boolean isAuthenticated(Authentication auth) {
        return auth != null && !(auth instanceof AnonymousAuthenticationToken) && auth.isAuthenticated();
    }

    private <A extends java.lang.annotation.Annotation> boolean hasAnnotation(HandlerMethod handlerMethod, Class<A> annotationClass) {
        A annotation = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), annotationClass);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), annotationClass);
        }
        return annotation != null;
    }
}
