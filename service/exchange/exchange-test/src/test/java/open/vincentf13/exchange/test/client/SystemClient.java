package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.admin.contract.SystemMaintenanceApi;

class SystemClient {
    private final SystemMaintenanceApi systemApi;

    SystemClient(String gatewayHost) {
        this.systemApi = FeignClientSupport.buildClient(
            SystemMaintenanceApi.class, gatewayHost + "/admin/api/admin/system");
    }

    void resetData() {
        FeignClientSupport.assertSuccess(systemApi.resetSystemData(), "resetSystemData");
    }
}
