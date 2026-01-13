package open.vincentf13.sdk.spring.cloud.openfeign.interceptor.jwt;

import java.util.Optional;

public interface FeignAuthorizationProvider {

  Optional<String> authorizationHeader();
}
