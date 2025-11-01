package open.vincentf13.sdk.auth.apikey.key;

import java.util.Set;

/**
 * Default implementation of {@link ApiKeyValidator} that uses a set of keys from configuration properties.
 */
public class PropertiesApiKeyValidator implements ApiKeyValidator {

    private final Set<String> validKeys;

    public PropertiesApiKeyValidator(ApiKeyProperties properties) {
        this.validKeys = properties.getValidKeys();
    }

    @Override
    public boolean isValid(String apiKey) {
        return apiKey != null && validKeys.contains(apiKey);
    }
}
