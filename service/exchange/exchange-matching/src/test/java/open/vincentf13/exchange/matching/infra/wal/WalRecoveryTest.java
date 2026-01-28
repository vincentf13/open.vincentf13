package open.vincentf13.exchange.matching.infra.wal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.matching.domain.instrument.Instrument;
import open.vincentf13.exchange.matching.domain.order.book.Order;
import open.vincentf13.exchange.matching.domain.order.book.OrderBook;
import open.vincentf13.exchange.matching.infra.cache.InstrumentCache;
import open.vincentf13.exchange.matching.service.InstrumentProcessor;
import open.vincentf13.sdk.core.mapper.ObjectMapperConfig;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import open.vincentf13.sdk.core.metrics.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 撮合引擎核心災難恢復整合測試
 */
class WalRecoveryTest {

  private static final Long INSTRUMENT_ID = 999999L;
  
  private static final Path WAL_PATH = Path.of("data/matching/wal-" + INSTRUMENT_ID + ".wal");
  private static final Path SNAPSHOT_PATH = Path.of("data/matching/snapshot-" + INSTRUMENT_ID + ".json");

  @BeforeEach
  void setup() throws Exception {
    // 1. 初始化 Metrics (避免 MTimer 報錯)
    Metrics.init(new SimpleMeterRegistry(), "test-app", "test-env");

    // 2. 初始化 ObjectMapper (使用 sdk-core 的標準配置)
    ObjectMapper objectMapper = new ObjectMapper();
    // 使用反射呼叫 ObjectMapperConfig.customize(objectMapper)
    Method customizeMethod = ObjectMapperConfig.class.getDeclaredMethod("customize", ObjectMapper.class);
    customizeMethod.setAccessible(true);
    customizeMethod.invoke(null, objectMapper);
    
    OpenObjectMapper.register(objectMapper);
    
    // 3. 初始化 InstrumentCache
    InstrumentCache instrumentCache = new InstrumentCache();
    Instrument instrument = Instrument.builder()
        .instrumentId(INSTRUMENT_ID)
        .symbol("TEST-USDT")
        .baseAsset(AssetSymbol.BTC)
        .quoteAsset(AssetSymbol.USDT)
        .contractSize(BigDecimal.ONE)
        .makerFee(new BigDecimal("0.001"))
        .takerFee(new BigDecimal("0.001"))
        .build();
    instrumentCache.putAllDomain(List.of(instrument));

    // 4. 清理環境
    cleanup();
  }

  void cleanup() throws IOException {
    Files.deleteIfExists(WAL_PATH);
    Files.deleteIfExists(SNAPSHOT_PATH);
  }

  @Test
  void testDisasterRecoveryAndIdempotency() throws IOException {
    try {
        // ==========================================
        // 階段一：正常運行 (生成快照 + WAL)
        // ==========================================
        System.out.println("--- 階段一：啟動 InstrumentProcessor 並處理訂單 ---");
        InstrumentProcessor processor1 = new InstrumentProcessor(INSTRUMENT_ID);
        processor1.init();

        // 1. 發送 1001 筆賣單，觸發 Snapshot 機制 (每 1000 筆觸發一次)
        // 這些訂單價格極高 (80000)，掛在 OrderBook 上不會成交
        List<Order> batchForSnapshot = LongStream.rangeClosed(1, 1001)
            .mapToObj(i -> Order.builder()
                .orderId(i)
                .userId(1L) // 添加 UserID
                .instrumentId(INSTRUMENT_ID)
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .price(BigDecimal.valueOf(80000))
                .quantity(BigDecimal.ONE)
                .originalQuantity(BigDecimal.ONE)
                .submittedAt(Instant.now())
                .build())
            .toList();
        
        processor1.processBatch(batchForSnapshot);
        
        // 驗證快照是否已生成
        assertThat(Files.exists(SNAPSHOT_PATH)).as("應該已生成 Snapshot 檔案").isTrue();

        // 2. 發送第 1002 筆訂單 (賣單，價格 60000)，這筆會在快照之後，只存在於 WAL
        Order walOnlyOrder = Order.builder()
            .orderId(1002L)
            .userId(1L) // 添加 UserID
            .instrumentId(INSTRUMENT_ID)
            .side(OrderSide.SELL)
            .type(OrderType.LIMIT)
            .price(BigDecimal.valueOf(60000))
            .quantity(BigDecimal.valueOf(10))
            .originalQuantity(BigDecimal.valueOf(10))
            .submittedAt(Instant.now())
            .build();
        
        processor1.processBatch(List.of(walOnlyOrder));
        
        // 關閉處理器 1 (模擬應用程式關閉)
        processor1.shutdown();

        // ==========================================
        // 階段二：崩潰重啟與恢復
        // ==========================================
        System.out.println("--- 階段二：模擬崩潰重啟 ---");
        InstrumentProcessor processor2 = new InstrumentProcessor(INSTRUMENT_ID);
        processor2.init(); // 這裡應該會自動載入 Snapshot 並重放 WAL

        // 透過 Reflection 獲取 OrderBook 進行驗證
        OrderBook recoveredBook = (OrderBook) ReflectionTestUtils.getField(processor2, "orderBook");
        assertThat(recoveredBook).isNotNull();

        // 驗證狀態：
        // 應該有 1002 筆掛單 (1001 筆來自快照 + 1 筆來自 WAL)
        assertThat(recoveredBook.dumpOpenOrders()).hasSize(1002);
        
        // 驗證 WAL 中的那筆訂單 (ID 1002) 是否存在且正確
        assertThat(recoveredBook.alreadyProcessed(1002L)).as("ID 1002 應標記為已處理").isTrue();
        
        // ==========================================
        // 階段三：冪等性測試 (Idempotency)
        // ==========================================
        System.out.println("--- 階段三：冪等性測試 ---");
        // 重新發送 ID 1002 (來自 WAL 的舊訂單)
        processor2.processBatch(List.of(walOnlyOrder));

        // 驗證狀態不應改變 (仍是 1002 筆掛單，且 ID 1002 的數量未變)
        // 註：若重複處理，可能會導致掛單重複或錯誤更新
        assertThat(recoveredBook.dumpOpenOrders()).hasSize(1002);
        
        // ==========================================
        // 階段四：新業務處理能力
        // ==========================================
        System.out.println("--- 階段四：新業務測試 ---");
        // 發送新買單 (ID 1003)，價格 70000，應該能與 ID 1002 (賣 60000) 成交
        Order newBuyOrder = Order.builder()
            .orderId(1003L)
            .userId(2L) // 添加不同的 UserID
            .instrumentId(INSTRUMENT_ID)
            .side(OrderSide.BUY)
            .type(OrderType.LIMIT)
            .price(BigDecimal.valueOf(70000))
            .quantity(BigDecimal.valueOf(5))
            .originalQuantity(BigDecimal.valueOf(5))
            .submittedAt(Instant.now())
            .build();
            
        processor2.processBatch(List.of(newBuyOrder));

        // 驗證成交結果：
        // 原本 ID 1002 賣單數量 10，成交 5，剩餘 5
        // 總掛單數應仍為 1002 (只是其中一筆數量變了)
        assertThat(recoveredBook.dumpOpenOrders()).hasSize(1002);
        
        // 檢查 ID 1002 的剩餘數量
        // 因為 dumpOpenOrders 排序規則：賣單 (Ask) 價格低優先。
        // ID 1002 價格 60000，其他 1001 筆是 80000。
        // 所以 ID 1002 應該在 Ask 隊列的第一位 (如果它是 Ask)
        // InstrumentProcessor 內部是用 bids/asks 分開存。
        // dumpOpenOrders 輸出順序是：Bids (Desc) -> Asks (Asc)。
        // 我們的訂單都是 SELL (Asks)。
        // 60000 (ID 1002) < 80000 (others)。
        // 所以 ID 1002 應該在 List 的前面 (如果是 Asks Asc)。
        // 讓我們檢查第一筆是否為 ID 1002
        Order firstOrder = recoveredBook.dumpOpenOrders().get(0);
        
        // 注意：dumpOpenOrders 邏輯是 bids.values() then asks.values()
        // 這裡只有 Asks。
        // Asks 是 TreeMap(BigDecimal::compareTo)，即升序。
        // 60000 < 80000。所以 60000 先出來。
        
        assertThat(firstOrder.getOrderId()).isEqualTo(1002L);
        assertThat(firstOrder.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5)); // 10 - 5 = 5

        System.out.println("所有測試通過：災難恢復、冪等性與新業務處理均正常。");

    } finally {
        cleanup();
    }
  }
}
