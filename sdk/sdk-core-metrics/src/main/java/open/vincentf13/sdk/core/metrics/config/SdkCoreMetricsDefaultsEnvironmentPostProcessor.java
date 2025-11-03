package open.vincentf13.sdk.core.metrics.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class SdkCoreMetricsDefaultsEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {

    @Override
    protected String getResourceLocation() {
        return "sdk-core-metrics-defaults.yaml";
    }
}
