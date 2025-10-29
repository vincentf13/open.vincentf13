package open.vincentf13.common.sdk.spring.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.List;

public class OpenUserUtils {
  public static User currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof UsernamePasswordAuthenticationToken token) {
      return ((User)token.getPrincipal());
    }
    return null; // fallback
  }

  public static List<String> getAuthorities() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth.getAuthorities().stream()
        .map(a -> a.getAuthority())
        .toList();
  }
}