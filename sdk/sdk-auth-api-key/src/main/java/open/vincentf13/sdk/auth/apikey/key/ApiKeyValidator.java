package open.vincentf13.sdk.auth.apikey.key;

/**
 * Interface for validating API keys.
 * Services that expose private APIs should provide a bean implementing this interface.
 */
public interface ApiKeyValidator {

    /**
     * Validates the given API key.
     *
     * @param apiKey The API key to validate.
     * @return {@code true} if the API key is valid, {@code false} otherwise.
     */
    boolean isValid(String apiKey);
}
