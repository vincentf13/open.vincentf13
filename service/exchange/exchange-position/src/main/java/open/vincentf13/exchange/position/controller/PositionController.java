package open.vincentf13.exchange.position.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.sdk.rest.api.PositionApi;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.exchange.position.service.PositionIntentValidator;
import open.vincentf13.exchange.position.service.PositionQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PositionController implements PositionApi {

    private final PositionQueryService positionQueryService;

    @Override
    public OpenApiResponse<List<PositionResponse>> listByUser(Long userId) {
        return OpenApiResponse.success(positionQueryService.list(userId));
    }

    @Override
    public OpenApiResponse<PositionIntentResponse> determineIntent(PositionIntentRequest request) {
        PositionIntentValidator.validate(request);
        return OpenApiResponse.success(positionQueryService.determineIntent(request));
    }
}
