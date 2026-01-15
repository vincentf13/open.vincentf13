package open.vincentf13.exchange.risk.service;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.infra.RiskErrorCode;
import open.vincentf13.exchange.risk.infra.persistence.repository.RiskLimitRepository;
import open.vincentf13.sdk.core.exception.OpenException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class RiskLimitQueryService {

  private final RiskLimitRepository riskLimitRepository;

import java.util.List;
import java.util.Map;
...
  public RiskLimit getRiskLimitByInstrumentId(@NotNull Long instrumentId) {
    return riskLimitRepository
        .findByInstrumentId(instrumentId)
        .orElseThrow(
            () ->
                OpenException.of(
                    RiskErrorCode.RISK_LIMIT_NOT_FOUND, Map.of("instrumentId", instrumentId)));
  }

  public List<RiskLimit> getAllRiskLimits() {
    return riskLimitRepository.findAll();
  }
}
