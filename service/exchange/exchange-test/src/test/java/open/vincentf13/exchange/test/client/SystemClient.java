package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.admin.contract.SystemMaintenanceApi;
import open.vincentf13.exchange.test.client.utils.FeignClientSupport;

public class SystemClient extends BaseClient {
    private final SystemMaintenanceApi systemApi;

    public SystemClient(String host) {
        super(host);
        this.systemApi = FeignClientSupport.buildClient(
            SystemMaintenanceApi.class, host + "/admin/api/admin/system");
    }

    public void resetData() {
        FeignClientSupport.assertSuccess(systemApi.resetSystemData(), "resetSystemData");
    }
}
