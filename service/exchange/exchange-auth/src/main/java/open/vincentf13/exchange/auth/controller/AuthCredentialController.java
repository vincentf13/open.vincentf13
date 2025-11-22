package open.vincentf13.exchange.auth.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.auth.sdk.rest.api.AuthCredentialApi;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareResponse;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialResponse;
import open.vincentf13.exchange.auth.service.AuthCredentialCommandService;
import open.vincentf13.exchange.auth.service.AuthCredentialQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth-credential")
@RequiredArgsConstructor
public class AuthCredentialController implements AuthCredentialApi {

    private final AuthCredentialCommandService authCredentialCommandService;
    private final AuthCredentialQueryService authCredentialQueryService;

    @Override
    public OpenApiResponse<AuthCredentialPrepareResponse> prepare(AuthCredentialPrepareRequest request) {
        return OpenApiResponse.success(authCredentialQueryService.prepare(request));
    }

    @Override
    public OpenApiResponse<AuthCredentialResponse> create(AuthCredentialCreateRequest request) {
        return OpenApiResponse.success(authCredentialCommandService.create(request));
    }
}
