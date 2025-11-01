package open.vincentf13.exchange.auth.config;

import open.vincentf13.exchange.auth.security.AuthUserDetailsService;
import open.vincentf13.exchange.auth.security.PasswordChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthSecurityConfiguration {

    @Bean
    public PasswordChecker authCredentialAuthenticationProvider(AuthUserDetailsService userDetailsService,
                                                                PasswordEncoder passwordEncoder) {
        PasswordChecker provider = new PasswordChecker(passwordEncoder);
        provider.setUserDetailsService(userDetailsService);
        provider.setHideUserNotFoundExceptions(false);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http,
                                                       PasswordChecker passwordChecker) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.authenticationProvider(passwordChecker);
        return builder.build();
    }
}
