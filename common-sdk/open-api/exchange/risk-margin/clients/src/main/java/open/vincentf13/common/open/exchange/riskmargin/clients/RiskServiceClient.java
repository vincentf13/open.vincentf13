package open.vincentf13.common.open.exchange.riskmargin.clients;

import open.vincentf13.common.open.exchange.riskmargin.interfaces.RiskCheckRequestDto;

/**
 * Risk service client generated from the shared contract.
 */
public interface RiskServiceClient {

    boolean validate(RiskCheckRequestDto request);
}
