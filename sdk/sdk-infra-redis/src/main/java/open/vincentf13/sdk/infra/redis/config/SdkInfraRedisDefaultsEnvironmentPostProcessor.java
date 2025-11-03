package open.vincentf13.sdk.infra.redis.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class SdkInfraRedisDefaultsEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {

    @Override
    protected String getResourceLocation() {
        return "sdk-infra-redis-defaults.yaml";
    }
}
