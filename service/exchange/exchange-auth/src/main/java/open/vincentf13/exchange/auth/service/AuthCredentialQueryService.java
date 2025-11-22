package open.vincentf13.exchange.auth.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.auth.domain.service.AuthCredentialDomainService;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareResponse;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthCredentialQueryService {

    private final AuthCredentialDomainService authCredentialDomainService;

    @Transactional(readOnly = true)
    public AuthCredentialPrepareResponse prepare(AuthCredentialPrepareRequest request) {
        OpenValidator.validateOrThrow(request);
        AuthCredentialDomainService.PreparedCredential prepared =
                authCredentialDomainService.prepareCredential(request.secret());
        return new AuthCredentialPrepareResponse(prepared.secretHash(), prepared.salt());
    }
}
