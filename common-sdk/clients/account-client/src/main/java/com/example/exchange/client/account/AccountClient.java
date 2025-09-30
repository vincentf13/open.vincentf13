package com.example.exchange.client.account;

import com.example.exchange.api.account.AccountBalanceDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client generated from account API contract (placeholder).
 */
@FeignClient(name = "account-service", path = "/accounts")
public interface AccountClient {

    @GetMapping("/{accountId}/balances/{asset}")
    AccountBalanceDto getBalance(@PathVariable("accountId") String accountId,
                                 @PathVariable("asset") String asset);
}
