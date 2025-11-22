package open.vincentf13.exchange.auth.domain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    private String generateSalt() {
        return UUID.randomUUID().toString();
    }

    private String hashSecret(String secret, String salt) {
        return passwordEncoder.encode(secret + ':' + salt);
    }

    public record PreparedCredential(String secretHash, String salt) { }
}
