package open.vincentf13.exchange.auth.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialResponse;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
@RequestMapping("/api/auth/credentials")
public interface AuthCredentialApi {

    @PostMapping
    OpenApiResponse<AuthCredentialResponse> create(@Valid @RequestBody AuthCredentialCreateRequest request);

    @GetMapping
    OpenApiResponse<AuthCredentialResponse> find(@RequestParam("userId") @NotNull Long userId,
                                                 @RequestParam("type") @NotNull AuthCredentialType credentialType);
}
