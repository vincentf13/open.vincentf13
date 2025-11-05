package open.vincentf13.exchange.auth.security;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import open.vincentf13.exchange.auth.infra.persistence.repository.AuthCredentialRepository;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialType;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserResponse;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserStatus;
import open.vincentf13.exchange.user.sdk.rest.client.ExchangeUserClient;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUser;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final AuthCredentialRepository authCredentialRepository;
    private final ExchangeUserClient exchangeUserClient;

    /**
     * 拋出 AuthenticationException 的子類，會由錯誤處理器處理
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (!StringUtils.hasText(email)) {
            throw new UsernameNotFoundException("Email must not be blank");
        }

        UserResponse user = getUserResponse(email);
        Long userId = user.id();
        if (userId == null) {
            throw new UsernameNotFoundException("User id missing for email " + email);
        }
        UserStatus status = user.status();
        if (status == UserStatus.LOCKED) {
            throw new LockedException("User is locked");
        }
        if (status == UserStatus.DISABLED) {
            throw new DisabledException("User is disabled");
        }

        AuthCredential credential = authCredentialRepository.findOne(AuthCredential.builder()
                                                                             .userId(userId)
                                                                             .credentialType(AuthCredentialType.PASSWORD)
                                                                             .build())
                .orElseThrow(() -> new UsernameNotFoundException("Credential not found for user " + email));

        if (!StringUtils.hasText(credential.getSecretHash()) || !StringUtils.hasText(credential.getSalt())) {
            throw new UsernameNotFoundException("Credential incomplete for user " + email);
        }

        if (!"ACTIVE".equalsIgnoreCase(credential.getStatus())) {
            throw new DisabledException("Credential is not active for user " + email);
        }

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new OpenJwtLoginUser(userId,
                                    user.email(),
                                    credential.getSecretHash(),
                                    credential.getSalt(),
                                    true,
                                    true,
                                    authorities);
    }

    private UserResponse getUserResponse(String email) {
        OpenApiResponse<UserResponse> response;
        try {
            response = exchangeUserClient.findByEmail(email);
        } catch (Exception ex) {
            throw new UsernameNotFoundException("Failed to query user service", ex);
        }

        if (response == null || !response.isSuccess() || response.data() == null) {
            throw new UsernameNotFoundException("User not found in user service: " + email);
        }

        UserResponse user = response.data();
        return user;
    }
}
