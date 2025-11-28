package open.vincentf13.exchange.auth.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.auth.domain.service.AuthCredentialDomainService;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class AuthCredentialQueryService {
    
    private final AuthCredentialDomainService authCredentialDomainService;
    
    @Transactional(readOnly = true)
    public AuthCredentialPrepareResponse prepare(@NotNull @Valid AuthCredentialPrepareRequest request) {
        AuthCredentialDomainService.PreparedCredential prepared =
                authCredentialDomainService.prepareCredential(request.secret());
        return new AuthCredentialPrepareResponse(prepared.secretHash(), prepared.salt());
    }
}
