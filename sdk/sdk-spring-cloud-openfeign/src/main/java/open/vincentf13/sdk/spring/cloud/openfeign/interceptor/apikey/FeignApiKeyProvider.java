package open.vincentf13.sdk.spring.cloud.openfeign.interceptor.apikey;

import java.util.Optional;

public interface FeignApiKeyProvider {
    Optional<String> apiKey();
}
