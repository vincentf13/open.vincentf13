package com.example.exchange.client.risk;

import com.example.exchange.api.risk.RiskCheckRequestDto;

/**
 * Risk service client generated from the shared contract.
 */
public interface RiskServiceClient {

    boolean validate(RiskCheckRequestDto request);
}
