package open.vincentf13.exchange.user.sdk.rest.client;


import open.vincentf13.exchange.user.sdk.rest.api.UserApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Feign client for calling the exchange-user service. The contract is inherited
 * from {@link UserApi}, so all request mappings stay in one place.
 *
 * Configuration notes:
 * - By default the client resolves the target service by name ("exchange-user").
 *   Override `exchange.user.client.name` or `exchange.user.client.url` if needed.
 * - The shared `sdk-spring-cloud-openfeign` module supplies global interceptors
 *   and error handling; additional per-client config can be supplied if required.
 */
@FeignClient(
        contextId = "exchangeUserClient",
        name = "${exchange.user.client.name:exchange-user}",
        url = "${exchange.user.client.url:}"
)
public interface ExchangeUserClient extends UserApi {
}
