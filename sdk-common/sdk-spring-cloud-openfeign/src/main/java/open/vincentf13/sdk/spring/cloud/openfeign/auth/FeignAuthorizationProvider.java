package open.vincentf13.sdk.spring.cloud.openfeign.auth;

import java.util.Optional;

public interface FeignAuthorizationProvider {

    Optional<String> authorizationHeader();
}
