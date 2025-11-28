package open.vincentf13.sdk.auth.auth;

import jakarta.servlet.http.HttpServletRequest;
import open.vincentf13.sdk.auth.jwt.token.JwtToken;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

/**
 授權管理器：透過解析控制器處理器上的註解判斷請求需滿足的認證機制。
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
    public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier,
                                       RequestAuthorizationContext object) {
        HttpServletRequest request = object.getRequest();
        
        HandlerMethod handlerMethod = resolveHandlerMethod(request); // 根據請求解析出對應的 Controller 方法
        if (handlerMethod == null) {
            return new AuthorizationDecision(isAuthenticated(authenticationSupplier.get())); // 找不到處理器時退回一般認證邏輯
        }
        
        if (BasicErrorController.class.isAssignableFrom(handlerMethod.getBeanType())) {
            return new AuthorizationDecision(true);
        }
        
        List<AuthRequirement> requirements = resolveRequirements(handlerMethod); // 蒐集方法及類別宣告的認證需求
        
        if (requirements.isEmpty()) {
            return new AuthorizationDecision(isAuthenticated(authenticationSupplier.get()));
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
            granted = isAuthenticated(authenticationSupplier.get());
        } else if (requireAll) {
            granted = available.containsAll(required); // requireAll=true 時需全部符合
        } else {
            granted = required.stream().anyMatch(available::contains); // 否則任一符合即可
        }
        
        return new AuthorizationDecision(granted); // 返回授權結果
    }
    
    private HandlerMethod resolveHandlerMethod(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (attribute instanceof HandlerMethod handlerMethod) {
            return handlerMethod;
        }
        
        // 先嘗試透過 mapping 快取直接比對，避免在 Security Filter 階段無法解析 Handler。
        try {
            ServletRequestPathUtils.parseAndCache(request);
        } catch (Exception ignored) {
        }
        
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String lookupPath;
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            lookupPath = requestUri.substring(contextPath.length());
        } else {
            lookupPath = requestUri;
        }
        
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            RequestMappingInfo matching = info.getMatchingCondition(request);
            if (matching != null) {
                return entry.getValue();
            }
            
            if (info.getPatternValues().contains(lookupPath)) {
                return entry.getValue();
            }
        }
        
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain != null && chain.getHandler() instanceof HandlerMethod hm) {
                return hm;
            }
        } catch (Exception ignored) {
        }
        
        return null;
    }
    
    /**
     從方法與類別層級合併所有 {@link AuthRequirement} 設定，好讓類別註解能成為全域預設，方法註解可覆寫或補充。
     */
    private List<AuthRequirement> resolveRequirements(HandlerMethod handlerMethod) {
        LinkedHashSet<AuthRequirement> merged = new LinkedHashSet<>();
        Method method = handlerMethod.getMethod();
        
        merged.addAll(AnnotatedElementUtils.findAllMergedAnnotations(
                method, AuthRequirement.class));
        merged.addAll(AnnotatedElementUtils.findAllMergedAnnotations(
                handlerMethod.getBeanType(), AuthRequirement.class));
        
        collectInterfaceRequirements(handlerMethod.getBeanType(), method, merged, new HashSet<>());
        collectSuperclassMethodRequirements(method, merged);
        return new ArrayList<>(merged);
    }
    
    /**
     將請求中已透過 Filter 標記的認證方式（例如 API Key）與 Spring Security 的登入狀態合併。
     */
    private EnumSet<AuthType> resolveAvailableAuthTypes(HttpServletRequest request,
                                                        Authentication authentication) {
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
            if (authentication instanceof JwtToken) {
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
    
    private void collectInterfaceRequirements(Class<?> type,
                                              Method method,
                                              LinkedHashSet<AuthRequirement> target,
                                              Set<Class<?>> visited) {
        if (type == null || type == Object.class) {
            return;
        }
        
        for (Class<?> iface : type.getInterfaces()) {
            if (!visited.add(iface)) {
                continue;
            }
            
            Method interfaceMethod = findMatchingMethod(iface, method);
            if (interfaceMethod != null) {
                target.addAll(AnnotatedElementUtils.findAllMergedAnnotations(
                        interfaceMethod, AuthRequirement.class));
            }
            target.addAll(AnnotatedElementUtils.findAllMergedAnnotations(
                    iface, AuthRequirement.class));
            
            collectInterfaceRequirements(iface, method, target, visited);
        }
        
        collectInterfaceRequirements(type.getSuperclass(), method, target, visited);
    }
    
    private void collectSuperclassMethodRequirements(Method method,
                                                     LinkedHashSet<AuthRequirement> target) {
        Class<?> declaringClass = method.getDeclaringClass().getSuperclass();
        while (declaringClass != null && declaringClass != Object.class) {
            Method superMethod = findMatchingMethod(declaringClass, method);
            if (superMethod != null) {
                target.addAll(AnnotatedElementUtils.findAllMergedAnnotations(
                        superMethod, AuthRequirement.class));
            }
            target.addAll(AnnotatedElementUtils.findAllMergedAnnotations(
                    declaringClass, AuthRequirement.class));
            declaringClass = declaringClass.getSuperclass();
        }
    }
    
    private Method findMatchingMethod(Class<?> type,
                                      Method method) {
        try {
            return type.getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }
}
