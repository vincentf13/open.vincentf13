package open.vincentf13.exchange.auth.domain.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialType;
import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthCredentialDomainService {

    private final PasswordEncoder passwordEncoder;

    public PreparedCredential prepareCredential(String secret) {
        String salt = generateSalt();
        String secretHash = hashSecret(secret, salt);
        return new PreparedCredential(secretHash, salt);
    }

    public AuthCredential createCredential(Long userId,
                                           AuthCredentialType credentialType,
                                           PreparedCredential prepared,
                                           String status) {
        return AuthCredential.builder()
                .userId(userId)
                .credentialType(credentialType)
                .secretHash(prepared.secretHash())
                .salt(prepared.salt())
                .status(status)
                .createdAt(Instant.now())
                .build();
    }

    private String generateSalt() {
        return UUID.randomUUID().toString();
    }

    private String hashSecret(String secret, String salt) {
        return passwordEncoder.encode(secret + ':' + salt);
    }

    public record PreparedCredential(String secretHash, String salt) { }
}
