package open.vincentf13.exchange.position.sdk.rest.api;

import jakarta.validation.Valid;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Validated
public interface PositionApi {

    @PostMapping("/intent")
    OpenApiResponse<PositionIntentResponse> determineIntent(@Valid @RequestBody PositionIntentRequest request);

    @GetMapping("/{userId}/{instrumentId}")
    OpenApiResponse<PositionResponse> getPosition(@PathVariable("userId") Long userId,
                                                   @PathVariable("instrumentId") Long instrumentId);

    @PostMapping("/{instrumentId}/leverage")
    OpenApiResponse<PositionLeverageResponse> adjustLeverage(@PathVariable("instrumentId") Long instrumentId,
                                                            @Valid @RequestBody PositionLeverageRequest request);
}
