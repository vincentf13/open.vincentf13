package open.vincentf13.exchange.matching.domain.book;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.matching.domain.model.MatchingOrder;
import open.vincentf13.exchange.matching.domain.model.OrderUpdate;
import open.vincentf13.exchange.matching.domain.model.Trade;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult {
    
    private MatchingOrder takerOrder;
    private List<Trade> trades = new ArrayList<>();
    private List<OrderUpdate> updates = new ArrayList<>();
    
    public MatchResult(MatchingOrder takerOrder) {
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
