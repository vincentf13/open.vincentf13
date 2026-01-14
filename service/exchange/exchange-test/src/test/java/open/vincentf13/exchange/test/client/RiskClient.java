package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.risk.sdk.rest.api.RiskApi;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskLimitResponse;
import open.vincentf13.exchange.test.client.utils.FeignClientSupport;

public class RiskClient extends BaseClient {
    public RiskClient(String host) {
        super(host);
    }

    public RiskLimitResponse getRiskLimit(String token, int instrumentId) {
        RiskApi riskApi = FeignClientSupport.buildClient(RiskApi.class, host + "/risk/api/risk", token);
        return FeignClientSupport.assertSuccess(riskApi.getRiskLimit((long) instrumentId), "risk.getLimit");
    }
}
