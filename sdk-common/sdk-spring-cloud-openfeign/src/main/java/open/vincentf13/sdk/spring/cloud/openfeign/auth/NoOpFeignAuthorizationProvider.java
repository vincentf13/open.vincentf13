package open.vincentf13.sdk.spring.cloud.openfeign.auth;

import java.util.Optional;

public class NoOpFeignAuthorizationProvider implements FeignAuthorizationProvider {

    @Override
    public Optional<String> authorizationHeader() {
        return Optional.empty();
    }
}
