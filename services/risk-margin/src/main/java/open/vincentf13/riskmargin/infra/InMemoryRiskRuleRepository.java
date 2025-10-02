package open.vincentf13.riskmargin.infra;

import open.vincentf13.riskmargin.domain.RiskDecision;
import open.vincentf13.riskmargin.domain.RiskRuleRepository;
import org.springframework.stereotype.Repository;

/**
 * Temporary in-memory rule implementation used for bootstrapping.
 */
@Repository
public class InMemoryRiskRuleRepository implements RiskRuleRepository {

    @Override
    public RiskDecision evaluate(String accountId, String symbol) {
        if (accountId == null || symbol == null) {
            return RiskDecision.denied("missing-parameters");
        }
        return RiskDecision.allowed();
    }
}
