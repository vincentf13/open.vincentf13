package open.vincentf13.sdk.core.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import open.vincentf13.sdk.core.CoreEventEnum;
import open.vincentf13.sdk.core.log.OpenLog;
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

/**
 * Base post-processor that loads YAML defaults from the classpath
 * and applies them with the lowest precedence so user overrides win.
 */
public abstract class AbstractYamlDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Override
    public final void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Resource resource = resolveResource();
        if (resource == null || !resource.exists()) {
            OpenLog.debug(CoreEventEnum.DEFAULTS_RESOURCE_MISSING, "resource", getResourceLocation());
            return;
        }

        try {
            var loadedSources = loader.load(getPropertySourceName(), resource);
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
                OpenLog.debug(CoreEventEnum.DEFAULTS_APPLIED, "resource", getResourceLocation(), "keys", defaults.keySet());
                environment.getPropertySources().addLast(new MapPropertySource(getPropertySourceName(), defaults));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load defaults from " + getResourceLocation(), ex);
        }
    }

    protected boolean hasUserValue(ConfigurableEnvironment environment, String name) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource == null) {
                continue;
            }
            if (getPropertySourceName().equals(propertySource.getName())) {
                continue;
            }
            if (propertySource.containsProperty(name)) {
                Object candidate = propertySource.getProperty(name);
                if (candidate instanceof String stringValue) {
                    if (StringUtils.hasText(stringValue)) {
                        return true;
                    }
                } else if (candidate != null) {
                    return true;
                }
            }
        }
        return false;
    }

    protected Resource resolveResource() {
        return new ClassPathResource(getResourceLocation());
    }

    protected abstract String getResourceLocation();

    protected String getPropertySourceName() {
        return getClass().getName();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
