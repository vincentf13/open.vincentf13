package open.vincentf13.exchange.test;

import open.vincentf13.exchange.admin.contract.InstrumentAdminApi;
import open.vincentf13.exchange.admin.contract.dto.InstrumentDetailResponse;

class AdminClient {
    private final String gatewayHost;

    AdminClient(String gatewayHost) {
        this.gatewayHost = gatewayHost;
    }

    InstrumentDetailResponse getInstrument(String token, int instrumentId) {
        InstrumentAdminApi adminApi = FeignClientSupport.buildClient(
            InstrumentAdminApi.class, gatewayHost + "/admin/api/admin/instruments", token);
        return FeignClientSupport.assertSuccess(adminApi.get((long) instrumentId), "instrument.get");
    }
}
