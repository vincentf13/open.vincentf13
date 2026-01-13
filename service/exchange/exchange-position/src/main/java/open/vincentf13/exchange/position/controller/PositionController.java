package open.vincentf13.exchange.position.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.sdk.rest.api.PositionApi;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionEventResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionReservationReleaseRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
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

  private final PositionCommandService positionCommandService;
  private final PositionQueryService positionQueryService;

  @Override
  public OpenApiResponse<PositionIntentResponse> determineIntentAndReserve(
      PositionIntentRequest request) {
    return OpenApiResponse.success(positionCommandService.determineIntentAndReserve(request));
  }

  @Override
  public OpenApiResponse<Object> releaseReservation(PositionReservationReleaseRequest request) {
    positionCommandService.releaseReservation(request);
    return OpenApiResponse.success(new Object());
  }

  @Override
  public OpenApiResponse<PositionResponse> getPosition(Long userId, Long instrumentId) {
    return OpenApiResponse.success(positionQueryService.getPosition(userId, instrumentId));
  }

  @Override
  public OpenApiResponse<List<PositionResponse>> getPositions(Long userId, Long instrumentId) {
    Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
    Long resolvedUserId = jwtUserId != null ? jwtUserId : userId;
    return OpenApiResponse.success(positionQueryService.getPositions(resolvedUserId, instrumentId));
  }

  @Override
  public OpenApiResponse<PositionEventResponse> getPositionEvents(Long positionId) {
    Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
    return OpenApiResponse.success(positionQueryService.getPositionEvents(jwtUserId, positionId));
  }
}
