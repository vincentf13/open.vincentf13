package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.admin.contract.SystemMaintenanceApi;
import open.vincentf13.exchange.test.client.utils.FeignClientSupport;

public class SystemClient extends BaseClient {
    public static void resetData() {
        SystemMaintenanceApi systemApi = FeignClientSupport.buildClient(
            SystemMaintenanceApi.class, host() + "/admin/api/admin/system");
        FeignClientSupport.assertSuccess(systemApi.resetSystemData(), "resetSystemData");
    }
}
