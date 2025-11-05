package open.vincentf13.sdk.auth.apikey;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.Set;

/**
 * Configuration properties for API Key authentication.
 */
@ConfigurationProperties(prefix = "security.api-key")
public class ApiKeyProperties {

    /**
     * Whether to enable API Key authentication.
     */
    private boolean enabled = false;

    /**
     * A set of valid API keys.
     */
    private Set<String> validKeys = Collections.emptySet();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getValidKeys() {
        return validKeys;
    }

    public void setValidKeys(Set<String> validKeys) {
        this.validKeys = validKeys;
    }
}
