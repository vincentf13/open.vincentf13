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
 * 撮合引擎核心災難恢復整合測試 (高覆蓋率版 V2.1)
 * 修正：撮合優先級邏輯與斷言
 */
class WalRecoveryTest {

  private static final Long INSTRUMENT_ID = 999999L;
  
  private static final Path WAL_PATH = Path.of("data/matching/wal-" + INSTRUMENT_ID + ".wal");
  private static final Path SNAPSHOT_PATH = Path.of("data/matching/snapshot-" + INSTRUMENT_ID + ".json");

  @BeforeEach
  void setup() throws Exception {
    Metrics.init(new SimpleMeterRegistry(), "test-app", "test-env");

    ObjectMapper objectMapper = new ObjectMapper();
    Method customizeMethod = ObjectMapperConfig.class.getDeclaredMethod("customize", ObjectMapper.class);
    customizeMethod.setAccessible(true);
    customizeMethod.invoke(null, objectMapper);
    OpenObjectMapper.register(objectMapper);
    
    InstrumentCache instrumentCache = new InstrumentCache();
    Instrument instrument = Instrument.builder()
        .instrumentId(INSTRUMENT_ID).symbol("TEST-USDT").baseAsset(AssetSymbol.BTC).quoteAsset(AssetSymbol.USDT)
        .contractSize(BigDecimal.ONE).makerFee(new BigDecimal("0.001")).takerFee(new BigDecimal("0.001"))
        .build();
    instrumentCache.putAllDomain(List.of(instrument));

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
        System.out.println("--- 階段一：啟動 InstrumentProcessor 並處理複雜數據訂單 ---");
        InstrumentProcessor processor1 = new InstrumentProcessor(INSTRUMENT_ID);
        processor1.init();

        for (long i = 1; i <= 1000; i++) {
            boolean isSell = i % 2 == 0;
            // 讓每 10 筆訂單共用一個價格 (i/10)，製造同價格多訂單場景，驗證 FIFO 順序
            BigDecimal price = (isSell ? new BigDecimal("20000.123") : new BigDecimal("10000.123"))
                .add(BigDecimal.valueOf(i / 10));
            
            BigDecimal qty = BigDecimal.valueOf(i); 
            BigDecimal origQty = qty.add(BigDecimal.valueOf(10)); 

            Order o = Order.builder()
                .orderId(i).userId(i % 10 + 1).instrumentId(INSTRUMENT_ID)
                .side(isSell ? OrderSide.SELL : OrderSide.BUY).type(OrderType.LIMIT)
                .price(price).quantity(qty).originalQuantity(origQty)
                .submittedAt(Instant.now()).build();
            
            expectedOrders.add(o);
        }
        processor1.processBatch(expectedOrders); // 直接發送 1000 筆觸發快照
        
        // ==========================================
        // 2. 發送 WAL 專屬訂單 (涵蓋買賣雙邊與邊界值)
        // ==========================================
        List<Order> walBatch = new ArrayList<>();
        
        // WAL 買單 (大額邊界)
        Order walBuy = Order.builder()
            .orderId(1001L).userId(99L).instrumentId(INSTRUMENT_ID).side(OrderSide.BUY).type(OrderType.LIMIT)
            .price(new BigDecimal("5000.999")).quantity(new BigDecimal("1000000")).originalQuantity(new BigDecimal("2000000"))
            .submittedAt(Instant.now()).build();
        
        // WAL 賣單 (小額邊界)
        Order walSell = Order.builder()
            .orderId(1002L).userId(100L).instrumentId(INSTRUMENT_ID).side(OrderSide.SELL).type(OrderType.LIMIT)
            .price(new BigDecimal("30000.888")).quantity(new BigDecimal("1")).originalQuantity(new BigDecimal("5"))
            .submittedAt(Instant.now()).build();
            
        walBatch.add(walBuy);
        walBatch.add(walSell);
        expectedOrders.addAll(walBatch);
        
        processor1.processBatch(walBatch);
        
        processor1.shutdown();

        // ==========================================
        // 階段二：崩潰重啟與數據全量驗證
        // ==========================================
        System.out.println("--- 階段二：模擬崩潰重啟與數據深度驗證 ---");
        InstrumentProcessor processor2 = new InstrumentProcessor(INSTRUMENT_ID);
        processor2.init();

        OrderBook recoveredBook = (OrderBook) ReflectionTestUtils.getField(processor2, "orderBook");
        List<Order> recoveredOrders = recoveredBook.dumpOpenOrders();

        // 數量驗證 (1000 Snapshot + 2 WAL = 1002)
        assertThat(recoveredOrders).hasSize(1002);

        assertOrderListsMatch(recoveredOrders, expectedOrders);

        System.out.println("數據驗證通過：1002 筆訂單 (含快照後買賣雙方 WAL) 已完全還原。");
        
        // ==========================================
        // 階段三：冪等性與成交測試
        // ==========================================
        System.out.println("--- 階段三：冪等性與撮合測試 ---");
        
        // 驗證冪等性 (重複發送 WAL 中的買單與賣單)
        processor2.processBatch(walBatch);
        // 再次全量驗證：確保狀態完全未變
        List<Order> idempotentOrders = recoveredBook.dumpOpenOrders();
        assertThat(idempotentOrders).hasSize(1002); 
        assertOrderListsMatch(idempotentOrders, expectedOrders);

        // 為了確保能成交到特定訂單，我們找出目前的 Best Bid (價格最高買單)
        // 根據生成邏輯，買單價格最高的是 i=999, 價格 10000.123 + 999 = 10999.123, Qty = 999
        Order bestBid = recoveredOrders.stream()
            .filter(Order::isBuy)
            .max(Comparator.comparing(Order::getPrice))
            .orElseThrow();
        
        assertThat(bestBid.getOrderId()).isEqualTo(999L);

        // 發送一個賣單成交掉 Best Bid (ID=999)
        Order takerSell = Order.builder()
            .orderId(3001L).userId(88L).instrumentId(INSTRUMENT_ID).side(OrderSide.SELL).type(OrderType.LIMIT)
            .price(bestBid.getPrice()) // 匹配最優買價
            .quantity(bestBid.getQuantity()) // 剛好全拿
            .originalQuantity(bestBid.getQuantity())
            .submittedAt(Instant.now()).build();
        
        processor2.processBatch(List.of(takerSell));

        // ID=999 應該被完全成交並移除，剩下 1001 筆
        List<Order> finalOrders = recoveredBook.dumpOpenOrders();
        assertThat(finalOrders.stream().anyMatch(o -> o.getOrderId() == 999L)).isFalse();
        assertThat(finalOrders).hasSize(1001);
        
        // 建構預期的最終狀態：移除 ID 999
        List<Order> expectedFinalOrders = new ArrayList<>(expectedOrders);
        expectedFinalOrders.removeIf(o -> o.getOrderId().equals(999L));
        
        // 全量驗證剩餘的 1001 筆訂單是否保持原樣
        assertOrderListsMatch(finalOrders, expectedFinalOrders);

        System.out.println("所有高覆蓋率測試場景驗證通過。");

    } finally {
        cleanup();
    }
  }

  private void assertOrderListsMatch(List<Order> actual, List<Order> expected) {
      // 1. 驗證順序 (Sequence Verification)
      // 模擬 OrderBook dump 邏輯：先 Bids (Price DESC, ID ASC)，後 Asks (Price ASC, ID ASC)
      List<Order> expectedSorted = new ArrayList<>(expected);
      
      Comparator<Order> orderBookComparator = (o1, o2) -> {
          // 買單 (Bid) 排在 賣單 (Ask) 之前
          if (o1.getSide() != o2.getSide()) {
               return o1.isBuy() ? -1 : 1;
          }
          // 同方向比較價格
          int priceCmp = o1.getPrice().compareTo(o2.getPrice());
          if (o1.isBuy()) {
              priceCmp = -priceCmp; // Bids: Price Descending
          }
          
          if (priceCmp != 0) return priceCmp;
          
          // 價格相同比較 ID (模擬 FIFO/Insertion Order)
          return o1.getOrderId().compareTo(o2.getOrderId());
      };
      
      expectedSorted.sort(orderBookComparator);
      
      // 驗證 actual 的順序是否嚴格符合預期的 OrderBook 排序
      assertThat(actual)
          .as("Verify OrderBook sequence (Price priority + Time priority)")
          .usingRecursiveComparison()
          .ignoringFields("submittedAt")
          .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .isEqualTo(expectedSorted);
  }
}