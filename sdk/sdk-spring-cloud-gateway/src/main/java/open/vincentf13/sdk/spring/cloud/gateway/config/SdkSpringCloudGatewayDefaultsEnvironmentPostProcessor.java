package open.vincentf13.sdk.spring.cloud.gateway.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class SdkSpringCloudGatewayDefaultsEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {

    @Override
    protected String getResourceLocation() {
        return "sdk-spring-cloud-gateway-defaults.yaml";
    }
}
