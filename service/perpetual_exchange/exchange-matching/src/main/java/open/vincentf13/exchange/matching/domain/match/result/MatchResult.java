package open.vincentf13.exchange.matching.domain.match.result;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.model.OrderUpdate;
import open.vincentf13.exchange.matching.domain.order.book.Order;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult {

  private Order takerOrder;
  private List<Trade> trades = new ArrayList<>();
  private List<OrderUpdate> updates = new ArrayList<>();

  public MatchResult(Order takerOrder) {
    this.takerOrder = takerOrder;
    this.trades = new ArrayList<>();
    this.updates = new ArrayList<>();
  }

  public void addTrade(Trade trade) {
    trades.add(trade);
  }

  public void addUpdate(OrderUpdate update) {
    updates.add(update);
  }
}
