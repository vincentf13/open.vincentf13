package open.vincentf13.client.risk;

import open.vincentf13.api.risk.RiskCheckRequestDto;

/**
 * Risk service client generated from the shared contract.
 */
public interface RiskServiceClient {

    boolean validate(RiskCheckRequestDto request);
}
