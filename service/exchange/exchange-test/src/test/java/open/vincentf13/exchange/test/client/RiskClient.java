package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.risk.sdk.rest.api.RiskApi;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskLimitResponse;
import open.vincentf13.exchange.test.client.utils.FeignClientSupport;

public class RiskClient extends BaseClient {
    private final RiskApi riskApi;

    public RiskClient(String host, String token) {
        super(host);
        this.riskApi = FeignClientSupport.buildClient(RiskApi.class, host + "/risk/api/risk", token);
    }

    public RiskLimitResponse getRiskLimit(int instrumentId) {
        return FeignClientSupport.assertSuccess(riskApi.getRiskLimit((long) instrumentId), "risk.getLimit");
    }
}
