package open.vincentf13.sdk.auth.apikey;

import open.vincentf13.sdk.auth.apikey.config.ApiKeyProperties;

import java.util.Set;

/**
 Default implementation of {@link ApiKeyValidator} that uses a set of keys from configuration properties.
 */
public class ApiKeyValidatorImpl implements ApiKeyValidator {
    
    private final Set<String> validKeys;
    
    public ApiKeyValidatorImpl(ApiKeyProperties properties) {
        this.validKeys = properties.getValidKeys();
    }
    
    @Override
    public boolean isValid(String apiKey) {
        return apiKey != null && validKeys.contains(apiKey);
    }
}
