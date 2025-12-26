package open.vincentf13.exchange.position.sdk.rest.api;

import jakarta.validation.Valid;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.sdk.auth.auth.Jwt;
import open.vincentf13.sdk.auth.auth.PrivateAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Validated
public interface PositionApi {
    
    @PostMapping("/intent")
    @PrivateAPI
    OpenApiResponse<PositionIntentResponse> prepareIntent(@Valid @RequestBody PositionIntentRequest request);
    
    @GetMapping("/{userId}/{instrumentId}")
    @PrivateAPI
    @Jwt
    OpenApiResponse<PositionResponse> getPosition(@PathVariable("userId") Long userId,
                                                  @PathVariable("instrumentId") Long instrumentId);

    @GetMapping("/{userId}")
    @PrivateAPI
    @Jwt
    OpenApiResponse<List<PositionResponse>> getPositions(@PathVariable("userId") Long userId,
                                                         @RequestParam(value = "instrumentId", required = false) Long instrumentId);
}
