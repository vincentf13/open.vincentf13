package open.vincentf13.riskmargin.domain;

/**
 * Domain repository for encapsulating risk rule evaluation sources.
 */
public interface RiskRuleRepository {

    RiskDecision evaluate(String accountId, String symbol);
}
