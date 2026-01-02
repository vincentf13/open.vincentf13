package open.vincentf13.sdk.spring.cloud.alibaba.nacos.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class NacosDefaultsEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {
    
    @Override
    protected String getResourceLocation() {
        return "sdk-nacos-defaults.yaml";
    }
}
