package open.vincentf13.exchange.matching.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.infra.persistence.mapper.TradeMapper;
import open.vincentf13.exchange.matching.infra.persistence.po.TradePO;
import open.vincentf13.exchange.matching.domain.model.Trade;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.infra.mysql.OpenMybatisBatchExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Repository
@Validated
@RequiredArgsConstructor
public class TradeRepository {
    
    private final TradeMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public void batchInsert(@NotEmpty List<@Valid Trade> trades) {
        trades.forEach(this::assignDefaults);
        List<TradePO> records = trades.stream()
                                      .map(t -> OpenObjectMapper.convert(t, TradePO.class))
                                      .toList();
        OpenMybatisBatchExecutor.execute(TradeMapper.class, records, TradeMapper::insert);
    }
    
    public void insertSelective(@NotNull @Valid Trade trade) {
        assignDefaults(trade);
        mapper.insert(OpenObjectMapper.convert(trade, TradePO.class));
    }
    
    private void assignDefaults(Trade trade) {
        if (trade.getTradeId() == null) {
            trade.setTradeId(idGenerator.newLong());
        }
        if (trade.getCreatedAt() == null) {
            trade.setCreatedAt(Instant.now());
        }
    }
}
