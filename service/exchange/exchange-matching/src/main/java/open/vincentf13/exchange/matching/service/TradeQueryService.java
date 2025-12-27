package open.vincentf13.exchange.matching.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.match.result.Trade;
import open.vincentf13.exchange.matching.infra.persistence.po.TradePO;
import open.vincentf13.exchange.matching.infra.persistence.repository.TradeRepository;
import open.vincentf13.exchange.matching.sdk.rest.dto.TradeResponse;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
public class TradeQueryService {

    private final TradeRepository tradeRepository;

    @Transactional(readOnly = true)
    public List<TradeResponse> listByOrderId(@NotNull Long userId,
                                             @NotNull Long orderId) {
        List<Trade> trades = tradeRepository.findBy(
                Wrappers.lambdaQuery(TradePO.class)
                        .and(wrapper ->
                                     wrapper.eq(TradePO::getMakerUserId, userId)
                                            .eq(TradePO::getOrderId, orderId)
                                            .or()
                                            .eq(TradePO::getTakerUserId, userId)
                                            .eq(TradePO::getCounterpartyOrderId, orderId))
                        .orderByDesc(TradePO::getExecutedAt));
        return OpenObjectMapper.convertList(trades, TradeResponse.class);
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> listByInstrument(@NotNull Long userId,
                                                @NotNull Long instrumentId) {
        List<Trade> trades = tradeRepository.findBy(
                Wrappers.lambdaQuery(TradePO.class)
                        .and(wrapper -> wrapper.eq(TradePO::getMakerUserId, userId)
                                               .or()
                                               .eq(TradePO::getTakerUserId, userId)));
        if (trades.isEmpty()) {
            return List.of();
        }
        List<Trade> sorted = new ArrayList<>(trades);
        Comparator<Trade> comparator = Comparator
                .comparing((Trade trade) -> instrumentId.equals(trade.getInstrumentId()) ? 0 : 1)
                .thenComparing(Trade::getExecutedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Trade::getTradeId, Comparator.nullsLast(Comparator.reverseOrder()));
        sorted.sort(comparator);
        return OpenObjectMapper.convertList(sorted, TradeResponse.class);
    }
}
