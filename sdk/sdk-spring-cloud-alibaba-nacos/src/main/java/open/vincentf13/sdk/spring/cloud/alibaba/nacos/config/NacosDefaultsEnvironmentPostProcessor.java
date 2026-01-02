package open.vincentf13.sdk.spring.cloud.alibaba.nacos.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

public class NacosDefaultsEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {
    
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 禁止 Nacos 初始化自己的日誌配置，避免 Log4j2 packages 警告與 Logback 衝突
        System.setProperty("nacos.logging.default.config.enabled", "false");
        super.postProcessEnvironment(environment, application);
    }

    @Override
    protected String getResourceLocation() {
        return "sdk-nacos-defaults.yaml";
    }
}
