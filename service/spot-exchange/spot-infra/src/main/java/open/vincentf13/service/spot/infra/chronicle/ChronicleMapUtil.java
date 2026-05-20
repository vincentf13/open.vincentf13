package open.vincentf13.service.spot.infra.chronicle;

import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ExternalMapQueryContext;
import net.openhft.chronicle.map.MapEntry;

/**
 * ChronicleMap 共用工具。
 */
public final class ChronicleMapUtil {

    private ChronicleMapUtil() {}

    /**
     * ChronicleMap put 的 zero-alloc 版本：跳過讀舊值。
     *
     * 標準 Map.put 契約必須返回舊值；ChronicleMap 為符合契約，會 reflectively
     * Constructor.newInstance() 一個 value 後 readMarshallable 進去 → 我們不在乎
     * 舊值卻被迫吃 alloc（JFR 實測 matching-disk-flusher 線程 100% 來自此路徑）。
     * 用 queryContext + replaceValue/insert 直接寫，跳過 entry.value().get()。
     */
    public static <K, V> void putNoRead(ChronicleMap<K, V> map, K key, V value) {
        try (ExternalMapQueryContext<K, V, ?> ctx = map.queryContext(key)) {
            ctx.updateLock().lock();
            MapEntry<K, V> entry = ctx.entry();
            if (entry != null) {
                ctx.replaceValue(entry, ctx.wrapValueAsData(value));
            } else {
                ctx.insert(ctx.absentEntry(), ctx.wrapValueAsData(value));
            }
        }
    }
}
