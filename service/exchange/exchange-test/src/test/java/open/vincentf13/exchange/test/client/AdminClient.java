package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.admin.contract.InstrumentAdminApi;
import open.vincentf13.exchange.admin.contract.dto.InstrumentDetailResponse;
import open.vincentf13.exchange.test.client.utils.FeignClientSupport;

public class AdminClient extends BaseClient {
    private final InstrumentAdminApi adminApi;

    public AdminClient(String host, String token) {
        super(host);
        this.adminApi = FeignClientSupport.buildClient(
            InstrumentAdminApi.class, host + "/admin/api/admin/instruments", token);
    }

    public InstrumentDetailResponse getInstrument(int instrumentId) {
        return FeignClientSupport.assertSuccess(adminApi.get((long) instrumentId), "instrument.get");
    }
}
