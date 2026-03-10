package open.vincentf13.service.spot.query.api;

import net.openhft.chronicle.map.ExternalMapQueryContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DataService {
    private final Storage storage;

    public DataService(Storage storage) {
        this.storage = storage;
    }

    public List<Balance> getBalances(long userId) {
        List<Balance> results = new ArrayList<>();
        Long mask = storage.userAssets().get(userId);
        if (mask == null || mask == 0) return results;

        for (int assetId = 0; assetId < 64; assetId++) {
            if (((mask >> assetId) & 1L) == 1) {
                BalanceKey key = new BalanceKey(userId, assetId);
                try (ExternalMapQueryContext<BalanceKey, Balance, ?> context = 
                         storage.balances().queryContext(key)) {
                    context.readLock().lock();
                    if (context.entry() != null) {
                        results.add(context.entry().value().get());
                    }
                }
            }
        }
        return results;
    }

    public List<Order> getOrders(long userId) {
        List<Order> results = new ArrayList<>();
        storage.activeOrders().keySet().forEach(orderId -> {
            try (ExternalMapQueryContext<Long, Order, ?> context = 
                     storage.orders().queryContext(orderId)) {
                context.readLock().lock();
                if (context.entry() != null) {
                    Order order = context.entry().value().get();
                    if (order.getUserId() == userId) {
                        results.add(order);
                    }
                }
            }
        });
        return results;
    }
}
