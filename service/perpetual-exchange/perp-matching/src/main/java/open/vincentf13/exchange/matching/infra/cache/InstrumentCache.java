package open.vincentf13.exchange.matching.infra.cache;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.matching.domain.instrument.Instrument;
import org.springframework.stereotype.Component;

/** Cache for Instrument data. */
@Component
public class InstrumentCache {

  private static InstrumentCache instance;

  private final Map<Long, Instrument> cache = new ConcurrentHashMap<>();

  public InstrumentCache() {
    instance = this;
  }

  /**
   * Static accessor for legacy/domain objects that cannot use DI.
   *
   * @param instrumentId The instrument ID
   * @return The Instrument, or null if not found
   */
  public static Instrument getInstrument(Long instrumentId) {
    if (instance == null) {
      return null;
    }
    return instance.get(instrumentId);
  }

  /**
   * Puts a list of instrument domain objects into the cache.
   *
   * @param instruments List of instrument domain objects
   */
  public void putAllDomain(List<Instrument> instruments) {
    if (instruments == null) {
      return;
    }

    instruments.forEach(
        instrument -> {
          validateContractSize(instrument);
          cache.put(instrument.getInstrumentId(), instrument);
        });
  }

  /**
   * Puts a list of instrument summaries into the cache. Converts DTOs to Domain entities.
   *
   * @param instrumentSummaries List of instrument summaries from Admin service
   */
  public void putAll(List<InstrumentSummaryResponse> instrumentSummaries) {
    if (instrumentSummaries == null) {
      return;
    }

    instrumentSummaries.forEach(
        dto -> {
          validateContractSize(dto);
          Instrument instrument = Instrument.from(dto);
          cache.put(instrument.getInstrumentId(), instrument);
        });
  }

  /**
   * Retrieves an instrument by its ID.
   *
   * @param instrumentId The instrument ID
   * @return The Instrument, or null if not found
   */
  public Instrument get(Long instrumentId) {
    return cache.get(instrumentId);
  }

  public void clear() {
    cache.clear();
  }

  public int size() {
    return cache.size();
  }

  private void validateContractSize(InstrumentSummaryResponse instrument) {
    if (instrument == null) {
      throw new IllegalArgumentException("Instrument must not be null");
    }
    BigDecimal contractSize = instrument.contractSize();
    if (contractSize == null || contractSize.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(
          "Invalid contractSize for instrumentId=" + instrument.instrumentId());
    }
  }

  private void validateContractSize(Instrument instrument) {
    if (instrument == null) {
      throw new IllegalArgumentException("Instrument must not be null");
    }
    BigDecimal contractSize = instrument.getContractSize();
    if (contractSize == null || contractSize.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(
          "Invalid contractSize for instrumentId=" + instrument.getInstrumentId());
    }
  }
}
