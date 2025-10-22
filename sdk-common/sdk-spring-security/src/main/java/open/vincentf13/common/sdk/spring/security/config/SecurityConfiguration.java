package open.vincentf13.common.sdk.spring.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 授權規則
                .authorizeHttpRequests(auth -> auth
                                               .requestMatchers("/login", "/public/**").permitAll()
                                               .anyRequest().authenticated()
                                      )
                // 表單登入
                .formLogin(form -> form
                                   .loginPage("/login")
                                   .defaultSuccessUrl("/")
                                   .permitAll()
                          )
                // 登出
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                       )
                // 關閉 CSRF（REST API 時建議關閉）
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
