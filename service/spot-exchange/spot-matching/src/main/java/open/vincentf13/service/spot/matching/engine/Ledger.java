package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 內存帳務處理器 (Ledger)
 職責：管理資產狀態，封裝跨帳戶的成交結算與退款邏輯
 */
@Service
public class Ledger {
    private final ChronicleMap<BalanceKey, Balance> balances = Storage.self().balances();
    private final ChronicleMap<Long, Long> userAssets = Storage.self().userAssets();

    private final Balance reusableBalance = new Balance();
    private final BalanceKey reusableKey = new BalanceKey();

    /** 
      執行成交結算流水線
      封裝了：資產搬運、溢價退款、捨入誤差歸集
     */
    public void settleTrade(long mUid, long tUid, long tradePrice, long tradeQty, 
                           byte takerSide, long takerPrice, long seq) {
        long floor = DecimalUtil.mulFloor(tradePrice, tradeQty);
        long ceil = DecimalUtil.mulCeil(tradePrice, tradeQty);

        if (takerSide == 0) { // Taker BUY
            // Taker 依照實際成交價結算，並獲得溢價退款
            tradeSettleWithRefund(tUid, Asset.USDT, ceil, DecimalUtil.mulCeil(takerPrice, tradeQty), Asset.BTC, tradeQty, seq);
            // Maker 賣出資產獲得金錢
            tradeSettle(mUid, Asset.BTC, tradeQty, Asset.USDT, floor, seq);
        } else { // Taker SELL
            // Taker 獲得資產，支付金錢 (掛單時已凍結)
            tradeSettle(tUid, Asset.BTC, tradeQty, Asset.USDT, floor, seq);
            // Maker 買入獲得資產
            tradeSettle(mUid, Asset.USDT, ceil, Asset.BTC, tradeQty, seq);
        }

        // 將由於精度 Scale 產生的捨入利潤歸入平台
        if (ceil > floor) addAvailable(PLATFORM_USER_ID, Asset.USDT, seq, ceil - floor);
    }

    private void tradeSettle(long userId, int assetOut, long amountOut, int assetIn, long amountIn, long seq) {
        updateBalance(userId, assetOut, seq, b -> b.setFrozen(Math.max(0, b.getFrozen() - amountOut)));
        updateBalance(userId, assetIn, seq, b -> b.setAvailable(b.getAvailable() + amountIn));
    }

    private void tradeSettleWithRefund(long userId, int assetOut, long amountOut, long totalFrozen, int assetIn, long amountIn, long seq) {
        updateBalance(userId, assetOut, seq, b -> {
            b.setFrozen(Math.max(0, b.getFrozen() - totalFrozen));
            long refund = totalFrozen - amountOut; 
            if (refund > 0) b.setAvailable(b.getAvailable() + refund);
        });
        updateBalance(userId, assetIn, seq, b -> b.setAvailable(b.getAvailable() + amountIn));
    }

    public void addAvailable(long userId, int assetId, long seq, long amount) {
        updateBalance(userId, assetId, seq, b -> b.setAvailable(b.getAvailable() + amount));
    }

    public boolean tryFreeze(long userId, int assetId, long seq, long amount) {
        if (amount <= 0) return true;
        boolean[] success = {false};
        updateBalance(userId, assetId, seq, b -> {
            if (b.getAvailable() >= amount) {
                b.setAvailable(b.getAvailable() - amount);
                b.setFrozen(b.getFrozen() + amount);
                success[0] = true;
            }
        });
        return success[0];
    }

    public void initBalance(long userId, int assetId, long seq) {
        updateBalance(userId, assetId, seq, b -> {});
    }

    private void updateBalance(long userId, int assetId, long seq, Consumer<Balance> action) {
        reusableKey.setUserId(userId); reusableKey.setAssetId(assetId);
        Balance balance = balances.getUsing(reusableKey, reusableBalance);
        if (balance == null) {
            balance = new Balance(); 
            balance.setAvailable(0); balance.setFrozen(0); balance.setVersion(0);
        }
        action.accept(balance);
        balance.setVersion(balance.getVersion() + 1);
        balance.setLastSeq(seq);
        balances.put(reusableKey, balance);
        updateUserAssetIndex(userId, assetId);
    }

    private void updateUserAssetIndex(long userId, int assetId) {
        if (assetId < 0 || assetId >= 64) return;
        long currentMask = userAssets.getOrDefault(userId, 0L);
        long newMask = currentMask | (1L << assetId);
        if (currentMask != newMask) userAssets.put(userId, newMask);
    }
}
