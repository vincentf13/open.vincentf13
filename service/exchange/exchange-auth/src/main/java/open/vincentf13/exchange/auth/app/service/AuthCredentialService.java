package open.vincentf13.exchange.auth.app.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.common.core.OpenMapstruct;
import open.vincentf13.common.core.exception.OpenServiceException;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialResponse;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialType;
import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import open.vincentf13.exchange.auth.domain.model.AuthErrorCode;
import open.vincentf13.exchange.auth.infra.persistence.repository.AuthCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthCredentialService {

    private final AuthCredentialRepository repository;

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

        AuthCredential credential = AuthCredential.builder()
                .userId(request.userId())
                .credentialType(request.credentialType())
                .secretHash(request.secretHash())
                .salt(request.salt())
                .status(request.status())
                .createdAt(Instant.now())
                .build();

        repository.insertSelective(credential);

        return OpenMapstruct.map(credential, AuthCredentialResponse.class);
    }

    @Transactional(readOnly = true)
    public AuthCredentialResponse find(Long userId, AuthCredentialType type) {
        AuthCredential probe = AuthCredential.builder()
                .userId(userId)
                .credentialType(type)
                .build();

        return repository.findOne(probe)
                .map(credential -> OpenMapstruct.map(credential, AuthCredentialResponse.class))
                .orElseThrow(() -> OpenServiceException.of(AuthErrorCode.AUTH_CREDENTIAL_NOT_FOUND,
                        "Credential not found for user " + userId + " type " + type));
    }
}
