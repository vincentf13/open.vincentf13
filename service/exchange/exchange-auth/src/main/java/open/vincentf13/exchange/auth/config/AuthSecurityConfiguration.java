package open.vincentf13.exchange.auth.config;

import open.vincentf13.exchange.auth.security.AuthCredentialAuthenticationProvider;
import open.vincentf13.exchange.auth.security.AuthUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthSecurityConfiguration {

    @Bean
    public AuthCredentialAuthenticationProvider authCredentialAuthenticationProvider(AuthUserDetailsService userDetailsService,
                                                                                     PasswordEncoder passwordEncoder) {
        AuthCredentialAuthenticationProvider provider = new AuthCredentialAuthenticationProvider(passwordEncoder);
        provider.setUserDetailsService(userDetailsService);
        provider.setHideUserNotFoundExceptions(false);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http,
                                                       AuthCredentialAuthenticationProvider authenticationProvider) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.authenticationProvider(authenticationProvider);
        return builder.build();
    }
}
