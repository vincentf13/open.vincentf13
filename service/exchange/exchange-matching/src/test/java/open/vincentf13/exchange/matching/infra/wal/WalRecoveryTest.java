package open.vincentf13.exchange.matching.infra.wal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.matching.domain.match.result.MatchResult;
import open.vincentf13.exchange.matching.domain.order.book.Order;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WalRecoveryTest {

  private static final Long TEST_INSTRUMENT_ID = 999999L;
  private static final Path WAL_PATH = Path.of("data/matching/wal-" + TEST_INSTRUMENT_ID + ".wal");

  @BeforeEach
  void setup() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OpenObjectMapper.register(objectMapper);
    
    cleanup();
  }

  @AfterEach
  void tearDown() throws IOException {
    cleanup();
  }

  void cleanup() throws IOException {
    if (Files.exists(WAL_PATH)) {
      Files.delete(WAL_PATH);
    }
  }

  @Test
  void testWalPersistenceAndRecovery() {
    // 1. Simulate Application Running & Writing to WAL
    InstrumentWal walInstance1 = new InstrumentWal(TEST_INSTRUMENT_ID);
    
    Order order1 = Order.builder()
        .orderId(101L)
        .instrumentId(TEST_INSTRUMENT_ID)
        .side(OrderSide.BUY)
        .type(OrderType.LIMIT)
        .price(BigDecimal.valueOf(50000))
        .quantity(BigDecimal.ONE)
        .submittedAt(Instant.now())
        .build();
    MatchResult result1 = new MatchResult(order1);

    Order order2 = Order.builder()
        .orderId(102L)
        .instrumentId(TEST_INSTRUMENT_ID)
        .side(OrderSide.SELL)
        .type(OrderType.MARKET)
        .price(BigDecimal.valueOf(49000))
        .quantity(BigDecimal.ONE)
        .submittedAt(Instant.now())
        .build();
    MatchResult result2 = new MatchResult(order2);

    walInstance1.appendBatch(List.of(result1));
    walInstance1.appendBatch(List.of(result2));

    assertThat(Files.exists(WAL_PATH)).as("WAL file should exist").isTrue();

    // 2. Simulate Crash & Restart (New Instance)
    InstrumentWal walInstance2 = new InstrumentWal(TEST_INSTRUMENT_ID);
    walInstance2.loadExisting();

    List<WalEntry> recoveredEntries = walInstance2.getEntries();

    // 3. Verify Data
    assertThat(recoveredEntries).hasSize(2);
    
    WalEntry entry1 = recoveredEntries.get(0);
    WalEntry entry2 = recoveredEntries.get(1);

    assertThat(entry1.getSeq()).isEqualTo(1L);
    assertThat(entry1.getMatchResult().getTakerOrder().getOrderId()).isEqualTo(101L);

    assertThat(entry2.getSeq()).isEqualTo(2L);
    assertThat(entry2.getMatchResult().getTakerOrder().getOrderId()).isEqualTo(102L);
    
    System.out.println("Verified WAL Recovery: Loaded " + recoveredEntries.size() + " entries successfully.");
  }
}
