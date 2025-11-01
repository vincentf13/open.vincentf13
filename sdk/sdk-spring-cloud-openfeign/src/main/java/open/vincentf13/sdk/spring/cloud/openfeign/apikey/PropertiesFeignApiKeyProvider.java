package open.vincentf13.sdk.spring.cloud.openfeign.apikey;

import org.springframework.util.StringUtils;

import java.util.Optional;

public class PropertiesFeignApiKeyProvider implements FeignApiKeyProvider {

    private final FeignApiKeyProperties properties;

    public PropertiesFeignApiKeyProvider(FeignApiKeyProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<String> apiKey() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        String value = properties.getValue();
        return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
    }
}
