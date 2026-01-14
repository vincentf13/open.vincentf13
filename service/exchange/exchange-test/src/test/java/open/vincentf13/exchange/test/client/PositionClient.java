package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.position.sdk.rest.api.PositionApi;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.exchange.test.FeignClientSupport;

import java.util.List;

class PositionClient {
    private final String gatewayHost;

    PositionClient(String gatewayHost) {
        this.gatewayHost = gatewayHost;
    }

    PositionResponse findPosition(String token, int instrumentId) {
        PositionApi positionApi = FeignClientSupport.buildClient(
            PositionApi.class, gatewayHost + "/position/api/positions", token);
        List<PositionResponse> positions = FeignClientSupport.assertSuccess(
            positionApi.getPositions(null, (long) instrumentId), "position.list");
        if (positions == null) {
            return null;
        }
        return positions.stream()
            .filter(item -> item.instrumentId() != null && item.instrumentId().intValue() == instrumentId)
            .findFirst()
            .orElse(null);
    }
}
