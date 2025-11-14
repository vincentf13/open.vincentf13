package open.vincentf13.exchange.position.sdk.rest.api;

import jakarta.validation.Valid;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Validated
@RequestMapping("/api/positions")
public interface PositionApi {

    @PostMapping("/intent")
    OpenApiResponse<PositionIntentResponse> determineIntent(@Valid @RequestBody PositionIntentRequest request);
}
