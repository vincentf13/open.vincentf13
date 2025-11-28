package open.vincentf13.sdk.core.test.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class SdkCoreTestDefaultsEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {
    
    @Override
    protected String getResourceLocation() {
        return "sdk-core-test-defaults.yaml";
    }
}
