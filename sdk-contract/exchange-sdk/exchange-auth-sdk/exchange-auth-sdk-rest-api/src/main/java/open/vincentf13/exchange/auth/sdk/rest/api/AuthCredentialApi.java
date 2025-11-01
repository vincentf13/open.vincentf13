package open.vincentf13.exchange.auth.sdk.rest.api;

import jakarta.validation.Valid;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareResponse;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialResponse;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import open.vincentf13.sdk.spring.security.auth.PrivateAPI;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Validated
@RequestMapping("/api/auth/credentials")
public interface AuthCredentialApi {

    @PostMapping("/prepare")
    @PrivateAPI
    OpenApiResponse<AuthCredentialPrepareResponse> prepare(@Valid @RequestBody AuthCredentialPrepareRequest request);

    @PostMapping
    OpenApiResponse<AuthCredentialResponse> create(@Valid @RequestBody AuthCredentialCreateRequest request);

}
