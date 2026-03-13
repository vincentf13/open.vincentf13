package open.vincentf13.service.spot.web.api;

import net.openhft.chronicle.map.ExternalMapQueryContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import open.vincentf13.service.spot.model.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DataService {
    public List<Balance> getBalances(long userId) {
        List<Balance> results = new ArrayList<>();
        Long mask = Storage.self().userAssets().get(userId);
        if (mask == null || mask == 0)
            return results;
        
        for (int assetId = 0; assetId < 64; assetId++) {
            if (((mask >> assetId) & 1L) == 1) {
                BalanceKey key = new BalanceKey(userId, assetId);
                try (ExternalMapQueryContext<BalanceKey, Balance, ?> context =
                             Storage.self().balances().queryContext(key)) {
                    context.readLock().lock();
                    if (context.entry() != null)
                        results.add(context.entry().value().get());
                }
            }
        }
        return results;
    }
    
    public List<Order> getOrders(long userId) {
        List<Order> results = new ArrayList<>();
        String activeOrderIdsStr = Storage.self().userActiveOrders().get(userId);
        
        if (activeOrderIdsStr == null || activeOrderIdsStr.isEmpty()) {
            return results;
        }

        String[] orderIds = activeOrderIdsStr.split(",");
        for (String idStr : orderIds) {
            if (idStr.isEmpty()) continue;
            try {
                long orderId = Long.parseLong(idStr);
                try (ExternalMapQueryContext<Long, Order, ?> context =
                             Storage.self().orders().queryContext(orderId)) {
                    context.readLock().lock();
                    if (context.entry() != null) {
                        Order order = context.entry().value().get();
                        if (order.getUserId() == userId) {
                            results.add(order);
                        }
                    }
                }
            } catch (NumberFormatException ignored) {
                // 忽略解析錯誤
            }
        }
        return results;
    }
}
