package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.risk.sdk.rest.api.RiskApi;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskLimitResponse;

class RiskClient {
    private final String gatewayHost;

    RiskClient(String gatewayHost) {
        this.gatewayHost = gatewayHost;
    }

    RiskLimitResponse getRiskLimit(String token, int instrumentId) {
        RiskApi riskApi = FeignClientSupport.buildClient(RiskApi.class, gatewayHost + "/risk/api/risk", token);
        return FeignClientSupport.assertSuccess(riskApi.getRiskLimit((long) instrumentId), "risk.getLimit");
    }
}
