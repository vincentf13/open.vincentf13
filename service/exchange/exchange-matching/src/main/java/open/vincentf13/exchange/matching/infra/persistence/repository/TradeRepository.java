package open.vincentf13.exchange.matching.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.match.result.Trade;
import open.vincentf13.exchange.matching.infra.persistence.mapper.TradeMapper;
import open.vincentf13.exchange.matching.infra.persistence.po.TradePO;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Repository
@Validated
@RequiredArgsConstructor
public class TradeRepository {

  private final TradeMapper mapper;
  private final DefaultIdGenerator idGenerator;

  public void batchInsert(@NotEmpty List<@Valid Trade> trades) {
    trades.forEach(this::assignDefaults);
    List<TradePO> records =
        trades.stream().map(t -> OpenObjectMapper.convert(t, TradePO.class)).toList();
    Db.saveBatch(records);
  }

  public void insertSelective(@NotNull @Valid Trade trade) {
    assignDefaults(trade);
    mapper.insert(OpenObjectMapper.convert(trade, TradePO.class));
  }

  public List<Trade> findBy(@NotNull LambdaQueryWrapper<TradePO> wrapper) {
    return mapper.selectList(wrapper).stream()
        .map(item -> OpenObjectMapper.convert(item, Trade.class))
        .toList();
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
