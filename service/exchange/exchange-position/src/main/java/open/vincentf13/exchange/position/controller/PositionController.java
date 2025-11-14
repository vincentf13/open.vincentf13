package open.vincentf13.exchange.position.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.sdk.rest.api.PositionApi;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.service.PositionIntentValidator;
import open.vincentf13.exchange.position.service.PositionQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PositionController implements PositionApi {

    private final PositionQueryService positionQueryService;

    @Override
    public OpenApiResponse<PositionIntentResponse> determineIntent(PositionIntentRequest request) {
        PositionIntentValidator.validate(request);
        return OpenApiResponse.success(positionQueryService.determineIntent(request));
    }
}
