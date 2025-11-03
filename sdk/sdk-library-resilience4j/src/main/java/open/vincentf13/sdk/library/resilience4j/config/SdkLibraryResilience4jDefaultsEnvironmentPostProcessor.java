package open.vincentf13.sdk.library.resilience4j.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class SdkLibraryResilience4jDefaultsEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {

    @Override
    protected String getResourceLocation() {
        return "sdk-library-resilience4j-defaults.yaml";
    }
}
