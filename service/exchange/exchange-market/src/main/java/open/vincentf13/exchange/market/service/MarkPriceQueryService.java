package open.vincentf13.exchange.market.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.sdk.rest.api.dto.MarkPriceResponse;
import open.vincentf13.exchange.market.infra.cache.MarkPriceCacheService;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MarkPriceQueryService {
    
    private final MarkPriceCacheService markPriceCacheService;
    
    public Optional<MarkPriceResponse> getMarkPrice(Long instrumentId) {
        if (instrumentId == null) {
            return Optional.empty();
        }
        return markPriceCacheService.getLatest(instrumentId)
                                    .map(snapshot -> OpenObjectMapper.convert(snapshot, MarkPriceResponse.class));
    }
}
