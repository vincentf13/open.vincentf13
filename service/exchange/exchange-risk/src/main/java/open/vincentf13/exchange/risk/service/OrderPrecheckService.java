package open.vincentf13.exchange.risk.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.infra.cache.InstrumentCache;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckRequest;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderPrecheckService {

    private final InstrumentCache instrumentCache;
    private final RiskLimitQueryService riskLimitQueryService;

    public OrderPrecheckResponse precheck(OrderPrecheckRequest request) {
        Optional<InstrumentSummaryResponse> instrumentOpt = instrumentCache.get(request.getInstrumentId());
        if (instrumentOpt.isEmpty()) {
            return new OrderPrecheckResponse(false, "Instrument not found");
        }
        InstrumentSummaryResponse instrument = instrumentOpt.get();
        if (!Boolean.TRUE.equals(instrument.tradable())) {
            return new OrderPrecheckResponse(false, "Instrument is not tradable");
        }

        if (request.getQuantity().remainder(instrument.lotSize()).compareTo(BigDecimal.ZERO) != 0) {
            return new OrderPrecheckResponse(false, "Quantity must be a multiple of lot size");
        }

        try {
            RiskLimit riskLimit = riskLimitQueryService.getRiskLimitByInstrumentId(request.getInstrumentId());

            if (riskLimit.getMaxOrderValue() != null && request.getPrice() != null) {
                BigDecimal multiplier = instrument.contractSize();
                if (multiplier == null) {
                    multiplier = BigDecimal.ONE;
                }

                BigDecimal orderValue = request.getQuantity().multiply(request.getPrice()).multiply(multiplier);

                if (orderValue.compareTo(riskLimit.getMaxOrderValue()) > 0) {
                    return new OrderPrecheckResponse(false, "Order value exceeds limit");
                }
            }
        } catch (Exception e) {
            return new OrderPrecheckResponse(false, "Risk limit check failed: " + e.getMessage());
        }

        return new OrderPrecheckResponse(true, "Passed");
    }
}
