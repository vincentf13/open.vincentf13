package open.vincentf13.sdk.spring.cloud.openfeign.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class SdkSpringCloudOpenfeignDefaultsEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {
    
    @Override
    protected String getResourceLocation() {
        return "sdk-spring-cloud-openfeign-defaults.yaml";
    }
}
