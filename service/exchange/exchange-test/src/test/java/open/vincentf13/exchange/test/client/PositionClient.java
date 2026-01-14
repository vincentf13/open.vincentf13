package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.position.sdk.rest.api.PositionApi;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.exchange.test.client.utils.FeignClientSupport;

import java.util.List;

public class PositionClient extends BaseClient {
    public PositionClient(String host) {
        super(host);
    }

    public PositionResponse findPosition(String token, int instrumentId) {
        PositionApi positionApi = FeignClientSupport.buildClient(
            PositionApi.class, host + "/position/api/positions", token);
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
