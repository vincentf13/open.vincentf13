package open.vincentf13.exchange.auth.app.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.common.core.OpenMapstruct;
import open.vincentf13.common.core.exception.OpenServiceException;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialResponse;
import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import open.vincentf13.exchange.auth.domain.model.AuthErrorCode;
import open.vincentf13.exchange.auth.infra.persistence.repository.AuthCredentialRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthCredentialService {

    private final AuthCredentialRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthCredentialResponse create(AuthCredentialCreateRequest request) {
        AuthCredential probe = AuthCredential.builder()
                .userId(request.userId())
                .credentialType(request.credentialType())
                .build();
        repository.findOne(probe).ifPresent(existing -> {
            throw OpenServiceException.of(AuthErrorCode.AUTH_CREDENTIAL_ALREADY_EXISTS,
                    "Credential already exists for user " + request.userId() + " type " + request.credentialType());
        });

        String salt = generateSalt();
        String secretHash = hashSecret(request.secret(), salt);

        AuthCredential credential = AuthCredential.builder()
                .userId(request.userId())
                .credentialType(request.credentialType())
                .secretHash(secretHash)
                .salt(salt)
                .status(request.status())
                .createdAt(Instant.now())
                .build();

        repository.insertSelective(credential);

        return OpenMapstruct.map(credential, AuthCredentialResponse.class);
    }

    private String generateSalt() {
        return UUID.randomUUID().toString();
    }

    private String hashSecret(String secret, String salt) {
        return passwordEncoder.encode(secret + ':' + salt);
    }
}
