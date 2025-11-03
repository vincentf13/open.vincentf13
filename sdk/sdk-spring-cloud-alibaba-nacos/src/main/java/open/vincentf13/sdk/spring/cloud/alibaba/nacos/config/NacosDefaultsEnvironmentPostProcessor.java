package open.vincentf13.sdk.spring.cloud.alibaba.nacos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 將 SDK 預設的 Nacos 配置以最低優先級塞入環境。若應用本身或外部已有設定，會保留使用者值。
 */
public class NacosDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(NacosDefaultsEnvironmentPostProcessor.class);

    private static final String PROPERTY_SOURCE_NAME = "open.vincentf13.sdk.nacos-defaults";
    private static final String RESOURCE_PATH = "sdk-nacos-defaults.yaml";

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Resource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            log.debug("Nacos defaults resource {} not found on classpath", RESOURCE_PATH);
            return;
        }

        try {
            var loadedSources = loader.load(PROPERTY_SOURCE_NAME, resource);
            Map<String, Object> defaults = new LinkedHashMap<>();

            for (PropertySource<?> source : loadedSources) {
                if (source instanceof EnumerablePropertySource<?> enumerable) {
                    for (String name : enumerable.getPropertyNames()) {
                        if (!hasUserValue(environment, name) && !defaults.containsKey(name)) {
                            defaults.put(name, enumerable.getProperty(name));
                        }
                    }
                }
            }

            if (!defaults.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Applying Nacos default properties: {}", defaults.keySet());
                }
                environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load Nacos default configuration", ex);
        }
    }

    private boolean hasUserValue(ConfigurableEnvironment environment, String name) {
        String value = environment.getProperty(name);
        return StringUtils.hasText(value);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
