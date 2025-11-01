package open.vincentf13.exchange.auth.api;

import jakarta.validation.Valid;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialPrepareRequest;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialPrepareResponse;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Validated
@RequestMapping("/api/auth/credentials")
public interface AuthCredentialApi {

    @PostMapping("/prepare")
    OpenApiResponse<AuthCredentialPrepareResponse> prepare(@Valid @RequestBody AuthCredentialPrepareRequest request);

    @PostMapping
    OpenApiResponse<AuthCredentialResponse> create(@Valid @RequestBody AuthCredentialCreateRequest request);

}
