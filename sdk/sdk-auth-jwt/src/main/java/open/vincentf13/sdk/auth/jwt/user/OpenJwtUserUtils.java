package open.vincentf13.sdk.auth.jwt.user;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.function.Supplier;

public class OpenJwtUserUtils {

    public static OpenJwtUser currentAuthUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof UsernamePasswordAuthenticationToken token)) {
            return null;
        }

        Object principal = token.getPrincipal();
        if (principal instanceof OpenJwtUser details) {
            return details;
        }
        return null;
    }

    public static List<String> getAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    public static Long currentUserId() {
        OpenJwtUser user = currentAuthUser();
        return user != null ? user.getUserId() : null;
    }

    public static Long currentUserIdOrThrow(Supplier<? extends RuntimeException> exceptionSupplier) {
        Long userId = currentUserId();
        if (userId == null) {
            throw exceptionSupplier.get();
        }
        return userId;
    }
}
