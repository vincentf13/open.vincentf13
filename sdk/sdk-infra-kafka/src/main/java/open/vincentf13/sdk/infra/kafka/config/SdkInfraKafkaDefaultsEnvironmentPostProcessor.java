package open.vincentf13.sdk.infra.kafka.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class SdkInfraKafkaDefaultsEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {
    
    @Override
    protected String getResourceLocation() {
        return "sdk-infra-kafka-defaults.yaml";
    }
}
