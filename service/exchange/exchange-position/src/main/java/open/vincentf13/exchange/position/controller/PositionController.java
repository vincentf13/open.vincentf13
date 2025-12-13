package open.vincentf13.exchange.position.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.sdk.rest.api.PositionApi;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.exchange.position.service.PositionQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController implements PositionApi {
    
    private final PositionQueryService positionQueryService;
    
    @Override
    public OpenApiResponse<PositionIntentResponse> prepareIntent(PositionIntentRequest request) {
        return OpenApiResponse.success(positionQueryService.prepareIntent(request));
    }
    
    @Override
    public OpenApiResponse<PositionResponse> getPosition(Long userId,
                                                         Long instrumentId) {
        return OpenApiResponse.success(positionQueryService.getPosition(userId, instrumentId));
    }
}
