package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.admin.contract.InstrumentAdminApi;
import open.vincentf13.exchange.admin.contract.dto.InstrumentDetailResponse;
import open.vincentf13.exchange.test.client.utils.FeignClientSupport;

public class AdminClient extends BaseClient {
    public static InstrumentDetailResponse getInstrument(int instrumentId) {
        InstrumentAdminApi adminApi = FeignClientSupport.buildClient(
            InstrumentAdminApi.class, host() + "/admin/api/admin/instruments");
        return FeignClientSupport.assertSuccess(adminApi.get((long) instrumentId), "instrument.get");
    }
}
