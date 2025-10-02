package open.vincentf13.riskmargin.app;

import open.vincentf13.api.risk.RiskCheckRequestDto;
import open.vincentf13.riskmargin.domain.RiskDecision;
import open.vincentf13.riskmargin.domain.RiskRuleRepository;
import org.springframework.stereotype.Service;

/**
 * Application layer orchestrating risk validation rules.
 */
@Service
public class RiskCheckService {

    private final RiskRuleRepository riskRuleRepository;

    public RiskCheckService(RiskRuleRepository riskRuleRepository) {
        this.riskRuleRepository = riskRuleRepository;
    }

    public boolean validate(RiskCheckRequestDto request) {
        RiskDecision decision = riskRuleRepository.evaluate(request.accountId(), request.symbol());
        return decision.isAllowed();
    }
}
