package open.vincentf13.exchange.auth.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialResponse;
import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import open.vincentf13.exchange.auth.infra.persistence.repository.AuthCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class AuthCredentialCommandService {

    private final AuthCredentialRepository repository;

    @Transactional
    public AuthCredentialResponse create(@Valid AuthCredentialCreateRequest request) {
        AuthCredential probe = AuthCredential.builder()
                .userId(request.userId())
                .credentialType(request.credentialType())
                .build();
        return repository.findOne(probe)
                .map(existing -> OpenObjectMapper.convert(existing, AuthCredentialResponse.class))
                .orElseGet(() -> {
                    AuthCredential credential = AuthCredential.create(
                            request.userId(),
                            request.credentialType(),
                            request.secretHash(),
                            request.salt(),
                            request.status()
                    );

                    Long credentialId = repository.insertSelective(credential);
                    credential.setId(credentialId);

                    return OpenObjectMapper.convert(credential, AuthCredentialResponse.class);
                });
    }
}
