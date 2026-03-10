package open.vincentf13.exchange.position.sdk.rest.api;

import jakarta.validation.Valid;
import open.vincentf13.exchange.position.sdk.rest.api.dto.*;
import open.vincentf13.sdk.auth.auth.Jwt;
import open.vincentf13.sdk.auth.auth.PrivateAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
public interface PositionApi {
    
    @PostMapping("/intent/reserve")
    @PrivateAPI
    OpenApiResponse<PositionIntentResponse> determineIntentAndReserve(
            @Valid @RequestBody PositionIntentRequest request);
    
    @PostMapping("/intent/release")
    @PrivateAPI
    OpenApiResponse<String> releaseReservation(
            @Valid @RequestBody PositionReservationReleaseRequest request);
    
    @GetMapping("/{userId}/{instrumentId}")
    @PrivateAPI
    @Jwt
    OpenApiResponse<PositionResponse> getPosition(
            @PathVariable("userId") Long userId,
            @PathVariable("instrumentId") Long instrumentId);
    
    @GetMapping("/{positionId}/events")
    @Jwt
    OpenApiResponse<PositionEventResponse> getPositionEvents(
            @PathVariable("positionId") Long positionId);
    
    @GetMapping
    @PrivateAPI
    @Jwt
    OpenApiResponse<List<PositionResponse>> getPositions(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "instrumentId", required = false) Long instrumentId);
}
