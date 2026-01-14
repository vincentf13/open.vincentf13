package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.admin.contract.InstrumentAdminApi;
import open.vincentf13.exchange.admin.contract.dto.InstrumentDetailResponse;
import open.vincentf13.exchange.test.client.utils.FeignClientSupport;

public class AdminClient extends BaseClient {
    public static InstrumentDetailResponse getInstrument(String token, int instrumentId) {
        InstrumentAdminApi adminApi = FeignClientSupport.buildClient(
            InstrumentAdminApi.class, host() + "/admin/api/admin/instruments", token);
        return FeignClientSupport.assertSuccess(adminApi.get((long) instrumentId), "instrument.get");
    }
}
