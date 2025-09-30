package com.example.exchange.riskmargin.app;

import com.example.exchange.api.risk.RiskCheckRequestDto;
import com.example.exchange.riskmargin.domain.RiskDecision;
import com.example.exchange.riskmargin.domain.RiskRuleRepository;
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
