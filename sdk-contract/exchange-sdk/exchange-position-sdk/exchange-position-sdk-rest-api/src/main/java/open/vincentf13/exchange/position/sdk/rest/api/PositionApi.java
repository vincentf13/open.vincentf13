package open.vincentf13.exchange.position.sdk.rest.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Validated
@RequestMapping("/api/positions")
public interface PositionApi {

    @GetMapping
    OpenApiResponse<List<PositionResponse>> listByUser(@RequestParam("userId") @NotNull Long userId);

    @PostMapping("/intent")
    OpenApiResponse<PositionIntentResponse> determineIntent(@Valid @RequestBody PositionIntentRequest request);
}
