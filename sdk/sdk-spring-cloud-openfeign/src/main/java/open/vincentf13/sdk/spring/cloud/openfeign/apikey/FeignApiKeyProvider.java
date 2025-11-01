package open.vincentf13.sdk.spring.cloud.openfeign.apikey;

import java.util.Optional;

public interface FeignApiKeyProvider {
    Optional<String> apiKey();
}
