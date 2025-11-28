package open.vincentf13.exchange.auth.sdk.rest.api;

import jakarta.validation.Valid;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareResponse;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialResponse;
import open.vincentf13.sdk.auth.auth.PrivateAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Validated
public interface AuthCredentialApi {
    
    @PostMapping("/prepare")
    @PrivateAPI
    OpenApiResponse<AuthCredentialPrepareResponse> prepare(@Valid @RequestBody AuthCredentialPrepareRequest request);
    
    @PostMapping
    @PrivateAPI
    OpenApiResponse<AuthCredentialResponse> create(@Valid @RequestBody AuthCredentialCreateRequest request);
    
}
