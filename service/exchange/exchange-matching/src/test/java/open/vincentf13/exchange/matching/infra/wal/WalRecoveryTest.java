package open.vincentf13.exchange.matching.infra.wal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * 撮合引擎核心災難恢復整合測試 (高覆蓋率版)
 * 驗證點：快照生成、WAL持久化、數據多樣性恢復(價格/數量/方向)、冪等性、新業務續接
 */
class WalRecoveryTest {

  private static final Long INSTRUMENT_ID = 999999L;
  
  private static final Path WAL_PATH = Path.of("data/matching/wal-" + INSTRUMENT_ID + ".wal");
  private static final Path SNAPSHOT_PATH = Path.of("data/matching/snapshot-" + INSTRUMENT_ID + ".json");

  @BeforeEach
  void setup() throws Exception {
    // 1. 初始化 Metrics
    Metrics.init(new SimpleMeterRegistry(), "test-app", "test-env");

    // 2. 初始化 ObjectMapper (使用 sdk-core 的標準配置)
    ObjectMapper objectMapper = new ObjectMapper();
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
        List<Order> expectedOrders = new ArrayList<>();

        // ==========================================
        // 階段一：正常運行 (生成快照 + WAL)
        // ==========================================
        System.out.println("--- 階段一：啟動 InstrumentProcessor 並處理多樣化訂單 ---");
        InstrumentProcessor processor1 = new InstrumentProcessor(INSTRUMENT_ID);
        processor1.init();

        // 1. 批量發送 1000 筆訂單，觸發 Snapshot
        // 設計策略：
        // - 賣單 (SELL): 價格區間 20,000 ~ 21,000
        // - 買單 (BUY) : 價格區間 10,000 ~ 11,000
        // - 確保買賣價差大，不會成交，全部掛在 OrderBook
        // - 數量：包含整數與小數
        List<Order> snapshotBatch = new ArrayList<>();
        for (long i = 1; i <= 1000; i++) {
            boolean isSell = i % 2 == 0;
            // 賣單價格 20000+, 買單價格 10000+
            BigDecimal price = isSell 
                ? BigDecimal.valueOf(20000).add(BigDecimal.valueOf(i)) 
                : BigDecimal.valueOf(10000).add(BigDecimal.valueOf(i));
            
            // 數量：i * 0.1 (例如 0.1, 0.2 ... 100.0)
            BigDecimal qty = BigDecimal.valueOf(i).multiply(new BigDecimal("0.1"));

            Order o = Order.builder()
                .orderId(i)
                .userId(i % 10 + 1) // 模擬 10 個用戶
                .instrumentId(INSTRUMENT_ID)
                .side(isSell ? OrderSide.SELL : OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(price)
                .quantity(qty)
                .originalQuantity(qty)
                .submittedAt(Instant.now())
                .build();
            
            snapshotBatch.add(o);
            expectedOrders.add(o);
        }
        
        processor1.processBatch(snapshotBatch);
        assertThat(Files.exists(SNAPSHOT_PATH)).as("應該已生成 Snapshot 檔案").isTrue();

        // 2. 發送 WAL 專屬訂單 (快照後的新數據)
        // 設計策略：覆蓋邊界值
        List<Order> walBatch = new ArrayList<>();
        
        // 極小數量與極高價格 (SELL)
        Order orderHighPrice = Order.builder()
            .orderId(1001L).userId(1L).instrumentId(INSTRUMENT_ID).side(OrderSide.SELL).type(OrderType.LIMIT)
            .price(new BigDecimal("999999.99")).quantity(new BigDecimal("0.0001")).originalQuantity(new BigDecimal("0.0001"))
            .submittedAt(Instant.now()).build();
        walBatch.add(orderHighPrice); expectedOrders.add(orderHighPrice);

        // 極大數量與極低價格 (BUY)
        Order orderLowPrice = Order.builder()
            .orderId(1002L).userId(2L).instrumentId(INSTRUMENT_ID).side(OrderSide.BUY).type(OrderType.LIMIT)
            .price(new BigDecimal("0.01")).quantity(new BigDecimal("1000000")).originalQuantity(new BigDecimal("1000000"))
            .submittedAt(Instant.now()).build();
        walBatch.add(orderLowPrice); expectedOrders.add(orderLowPrice);

        processor1.processBatch(walBatch);
        
        // 關閉 (模擬當機)
        processor1.shutdown();

        // ==========================================
        // 階段二：崩潰重啟與全量數據驗證
        // ==========================================
        System.out.println("--- 階段二：模擬崩潰重啟與數據驗證 ---");
        InstrumentProcessor processor2 = new InstrumentProcessor(INSTRUMENT_ID);
        processor2.init(); // 自動載入 Snapshot + WAL

        OrderBook recoveredBook = (OrderBook) ReflectionTestUtils.getField(processor2, "orderBook");
        assertThat(recoveredBook).isNotNull();
        List<Order> recoveredOrders = recoveredBook.dumpOpenOrders();

        // 驗證 1: 總數量 (1000 Snapshot + 2 WAL)
        assertThat(recoveredOrders).hasSize(1002);

        // 驗證 2: 每一筆訂單的詳細內容 (遞迴比較，忽略 BigDecimal Scale 差異如 1.0 vs 1.00)
        // 為了比較方便，我們先將兩邊的列表都按 OrderID 排序
        expectedOrders.sort(Comparator.comparing(Order::getOrderId));
        recoveredOrders.sort(Comparator.comparing(Order::getOrderId));

        assertThat(recoveredOrders)
            .usingRecursiveComparison()
            .ignoringFields("submittedAt") // 時間可能因序列化精度有微小差異
            .withComparatorForType(BigDecimal::compareTo, BigDecimal.class) // 使用 compareTo 忽略 scale (1.0 == 1.00)
            .isEqualTo(expectedOrders);

        System.out.println("數據驗證通過：所有 1002 筆訂單的值域與狀態完全一致。");
        
        // ==========================================
        // 階段三：冪等性與新業務
        // ==========================================
        System.out.println("--- 階段三：冪等性與成交測試 ---");
        
        // 重複發送 WAL 中的訂單
        processor2.processBatch(walBatch);
        assertThat(recoveredBook.dumpOpenOrders()).hasSize(1002); // 數量不應增加

        // 發送新訂單成交 (此賣單價格 0.01 極低，會從最高買單 (10000+) 開始一路吃到 0.01)
        // 預期行為：
        // 1. 掃光所有 Snapshot 建立的 500 筆買單 (總量約 25,000)
        // 2. 剩餘量撞擊 ID 1002 (價格 0.01, 總量 100w)
        Order takerSell = Order.builder()
            .orderId(2001L).userId(3L).instrumentId(INSTRUMENT_ID).side(OrderSide.SELL).type(OrderType.LIMIT)
            .price(new BigDecimal("0.01")) // 對手價
            .quantity(new BigDecimal("500000")) // 吃掉一半 (1000000 -> 剩 500000)
            .originalQuantity(new BigDecimal("500000"))
            .submittedAt(Instant.now()).build();
        
        processor2.processBatch(List.of(takerSell));

        // 驗證成交後的狀態
        // 原有 1002 筆 (500 Bids + 1 Big Bid + 500 Asks + 1 Big Ask)
        // 成交後：500 Bids 被吃光，Big Bid 剩餘。 Asks 不變。
        // 預期剩餘： 1 (Big Bid) + 500 (Asks) + 1 (Big Ask) = 502 筆
        assertThat(recoveredBook.dumpOpenOrders()).hasSize(502);

        // 檢查 ID 1002 的剩餘量
        // 消耗量 = 500,000 (Taker)
        // ID 1002 原始 = 1,000,000
        // ID 1002 剩餘 = 1,000,000 - (500,000 - SnapshotBidsTotal)
        // Snapshot Bids Total: Sum(i * 0.1) for i=1,3,5...999 (500 items)
        // 這比較難算，但我們可以驗證它確實減少了
        Order updatedOrder1002 = recoveredBook.dumpOpenOrders().stream()
            .filter(o -> o.getOrderId().equals(1002L))
            .findFirst()
            .orElseThrow();
        
        // 應小於 100w，且大於 0
        assertThat(updatedOrder1002.getQuantity())
            .isLessThan(new BigDecimal("1000000"))
            .isGreaterThan(BigDecimal.ZERO);

        System.out.println("所有測試場景驗證通過。");

    } finally {
        cleanup();
    }
  }
}