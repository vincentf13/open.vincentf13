//package open.vincentf13.common.sdk.spring.security.handler;
//
//import org.springframework.security.authentication.AccountExpiredException;
//import org.springframework.security.authentication.CredentialsExpiredException;
//import org.springframework.security.authentication.DisabledException;
//import org.springframework.security.authentication.LockedException;
//import org.springframework.security.core.userdetails.User;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//
//import java.util.Arrays;
//
//public class LoginUserDetailsService implements UserDetailsService {
//
//    private final UserRepository repo; // 你自己的 JPA/MyBatis Repo
//
//    @Override
//    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//        AppUser u = repo.findByUsername(username)
//                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
//
//        if (u.getStatus() == Status.FROZEN) throw new LockedException("帳號鎖定");
//        if (u.getStatus() == Status.REVIEWING) throw new DisabledException("帳號未啟用/已停用");
//        if (false) throw new AccountExpiredException("帳號過期");
//        if (false) throw new CredentialsExpiredException("密碼過期");
//
//        var authorities = Arrays.stream(u.getRoles().split(","))
//                .map(String::trim)
//                .filter(s -> !s.isEmpty())
//                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
//                .map(SimpleGrantedAuthority::new)
//                .toList();
//
//        return org.springframework.security.core.userdetails.User
//                .withUsername(u.getUsername())
//                .password(u.getPassword())
//                .authorities(authorities)
//                .accountExpired(false)
//                .accountLocked(false)
//                .credentialsExpired(false)
//                .disabled(!u.isEnabled())
//                .build();
//    }
//}
