package open.vincentf13.exchange.position.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.sdk.rest.api.PositionApi;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageResponse;
import open.vincentf13.exchange.position.service.PositionCommandService;
import open.vincentf13.exchange.position.service.PositionQueryService;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserHolder;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController implements PositionApi {

    private final PositionQueryService positionQueryService;
    private final PositionCommandService positionCommandService;

    @Override
    public OpenApiResponse<PositionIntentResponse> determineIntent(PositionIntentRequest request) {
        return OpenApiResponse.success(positionQueryService.determineIntent(request));
    }

    @Override
    public OpenApiResponse<PositionLeverageResponse> adjustLeverage(Long instrumentId,
                                                                    PositionLeverageRequest request) {
        Long userId = OpenJwtLoginUserHolder.currentUserIdOrThrow(() -> new IllegalStateException("UserId missing"));
        return OpenApiResponse.success(positionCommandService.adjustLeverage(userId, instrumentId, request));
    }
}
