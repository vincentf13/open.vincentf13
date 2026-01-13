package open.vincentf13.exchange.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.dto.InstrumentDetailResponse;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;
import open.vincentf13.exchange.admin.domain.model.Instrument;
import open.vincentf13.exchange.admin.infra.AdminErrorCode;
import open.vincentf13.exchange.admin.infra.persistence.po.InstrumentPO;
import open.vincentf13.exchange.admin.infra.persistence.repository.InstrumentRepository;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstrumentQueryService {

  private final InstrumentRepository instrumentRepository;

  @Transactional(readOnly = true)
  public List<InstrumentSummaryResponse> list(
      InstrumentStatus status, InstrumentType instrumentType) {
    var wrapper =
        Wrappers.<InstrumentPO>lambdaQuery()
            .eq(status != null, InstrumentPO::getStatus, status)
            .eq(instrumentType != null, InstrumentPO::getInstrumentType, instrumentType)
            .orderByAsc(InstrumentPO::getDisplayOrder)
            .orderByAsc(InstrumentPO::getInstrumentId);

    List<Instrument> instruments = instrumentRepository.findBy(wrapper);
    return OpenObjectMapper.convertList(instruments, InstrumentSummaryResponse.class);
  }

  @Transactional(readOnly = true)
  public InstrumentDetailResponse get(Long instrumentId) {
    var wrapper =
        Wrappers.<InstrumentPO>lambdaQuery().eq(InstrumentPO::getInstrumentId, instrumentId);
    Instrument instrument =
        instrumentRepository
            .findOne(wrapper)
            .orElseThrow(
                () ->
                    OpenException.of(
                        AdminErrorCode.INSTRUMENT_NOT_FOUND, Map.of("instrumentId", instrumentId)));
    return OpenObjectMapper.convert(instrument, InstrumentDetailResponse.class);
  }
}
