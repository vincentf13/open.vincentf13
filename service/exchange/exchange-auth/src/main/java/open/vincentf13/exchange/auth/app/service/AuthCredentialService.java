package open.vincentf13.exchange.auth.app.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.OpenMapstruct;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialPrepareRequest;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialPrepareResponse;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialResponse;
import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import open.vincentf13.exchange.auth.domain.service.AuthCredentialDomainService;
import open.vincentf13.exchange.auth.infra.persistence.repository.AuthCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthCredentialService {

    private final AuthCredentialRepository repository;
    private final AuthCredentialDomainService authCredentialDomainService;

    @Transactional(readOnly = true)
    public AuthCredentialPrepareResponse prepare(AuthCredentialPrepareRequest request) {
        AuthCredentialDomainService.PreparedCredential prepared = authCredentialDomainService.prepareCredential(request.secret());
        return new AuthCredentialPrepareResponse(prepared.secretHash(), prepared.salt());
    }

    @Transactional
    public AuthCredentialResponse create(AuthCredentialCreateRequest request) {
        AuthCredential probe = AuthCredential.builder()
                .userId(request.userId())
                .credentialType(request.credentialType())
                .build();
        return repository.findOne(probe)
                .map(existing -> OpenMapstruct.map(existing, AuthCredentialResponse.class))
                .orElseGet(() -> {
                    AuthCredentialDomainService.PreparedCredential prepared =
                            new AuthCredentialDomainService.PreparedCredential(request.secretHash(), request.salt());

                    AuthCredential credential = authCredentialDomainService.createCredential(
                            request.userId(),
                            request.credentialType(),
                            prepared,
                            request.status()
                    );

                    repository.insertSelective(credential);

                    return OpenMapstruct.map(credential, AuthCredentialResponse.class);
                });
    }
}
