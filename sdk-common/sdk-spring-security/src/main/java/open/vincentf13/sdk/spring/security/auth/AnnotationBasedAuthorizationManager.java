package open.vincentf13.sdk.spring.security.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import open.vincentf13.sdk.auth.jwt.token.model.JwtAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 授權管理器：透過解析控制器處理器上的註解判斷請求需滿足的認證機制。
 */
public class AnnotationBasedAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final RequestMappingHandlerMapping handlerMapping;

    public AnnotationBasedAuthorizationManager(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    /**
     * 核心邏輯：先找出對應 Handler 與其宣告的 {@link AuthRequirement}，再與當前請求可用的驗證方式比對。
     */
    public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier, RequestAuthorizationContext object) {
        HttpServletRequest request = object.getRequest();

        HandlerMethod handlerMethod = resolveHandlerMethod(request); // 根據請求解析出對應的 Controller 方法
        if (handlerMethod == null) {
            return new AuthorizationDecision(isAuthenticated(authenticationSupplier.get())); // 找不到處理器時退回一般認證邏輯
        }

        List<AuthRequirement> requirements = resolveRequirements(handlerMethod); // 蒐集方法及類別宣告的認證需求
        if (requirements.isEmpty()) {
            return new AuthorizationDecision(false); // 沒有額外註解時，禁止存取
        }

        // public api
        if (requirements.stream().anyMatch(req -> Arrays.asList(req.value()).contains(AuthType.NONE))) {
            return new AuthorizationDecision(true); // 任一註解標示為 NONE 就直接放行
        }

         // 當前已取得認證
        EnumSet<AuthType> available = resolveAvailableAuthTypes(request, authenticationSupplier.get());

        boolean requireAll = requirements.stream().anyMatch(AuthRequirement::requireAll); // 判斷是否需同時滿足全部模式
        EnumSet<AuthType> required = EnumSet.noneOf(AuthType.class);
        requirements.forEach(req -> required.addAll(Arrays.asList(req.value())));

        boolean granted;
        if (required.isEmpty()) {
            granted = false; // 未指定任何認證類型時視為禁止存取
        } else if (requireAll) {
            granted = available.containsAll(required); // requireAll=true 時需全部符合
        } else {
            granted = required.stream().anyMatch(available::contains); // 否則任一符合即可
        }

        return new AuthorizationDecision(granted); // 返回授權結果
    }

    private HandlerMethod resolveHandlerMethod(HttpServletRequest request) {
        try {
            Object handler = handlerMapping.getHandler(request);
            if (handler instanceof HandlerMethod handlerMethod) {
                return handlerMethod;
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 從方法與類別層級合併所有 {@link AuthRequirement} 設定，好讓類別註解能成為全域預設，方法註解可覆寫或補充。
     */
    private List<AuthRequirement> resolveRequirements(HandlerMethod handlerMethod) {
        List<AuthRequirement> merged = new ArrayList<>();
        // 擷取方法層級宣告的 @AuthRequirement（含重複註解與繼承來源）
        merged.addAll(AnnotatedElementUtils.findMergedRepeatableAnnotations(
                handlerMethod.getMethod(), AuthRequirement.class, AuthRequirement.class));
        merged.addAll(AnnotatedElementUtils.findMergedRepeatableAnnotations(
                handlerMethod.getBeanType(), AuthRequirement.class, AuthRequirement.class));
        return merged;
    }

    /**
     * 將請求中已透過 Filter 標記的認證方式（例如 API Key）與 Spring Security 的登入狀態合併。
     */
    private EnumSet<AuthType> resolveAvailableAuthTypes(HttpServletRequest request, Authentication authentication) {
        EnumSet<AuthType> available = EnumSet.noneOf(AuthType.class);

        Object attribute = request.getAttribute(AuthAttributes.AUTH_TYPES);
        if (attribute instanceof Set<?>) {
            ((Set<?>) attribute).stream()
                    .filter(Objects::nonNull)
                    .filter(AuthType.class::isInstance)
                    .map(AuthType.class::cast)
                    .forEach(available::add);
        }

        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {

            // 在Jwt filter 注入 SecurityContextHolder
            if (authentication instanceof JwtAuthenticationToken) {
                available.add(AuthType.JWT);
            } else {
                available.add(AuthType.SESSION);
            }
        }

        return available;
    }

    private boolean isAuthenticated(Authentication auth) {
        return auth != null && !(auth instanceof AnonymousAuthenticationToken) && auth.isAuthenticated();
    }
}
