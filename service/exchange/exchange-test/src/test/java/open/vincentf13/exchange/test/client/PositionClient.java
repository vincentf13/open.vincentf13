package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.position.sdk.rest.api.PositionApi;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.exchange.test.client.utils.FeignClientSupport;

import java.util.List;

public class PositionClient extends BaseClient {
    private final PositionApi positionApi;

    public PositionClient(String host, String token) {
        super(host);
        this.positionApi = FeignClientSupport.buildClient(
            PositionApi.class, host + "/position/api/positions", token);
    }

    public PositionResponse findPosition(int instrumentId) {
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
