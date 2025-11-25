package open.vincentf13.exchange.admin.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.domain.model.Instrument;
import open.vincentf13.exchange.admin.infra.AdminErrorCodeEnum;
import open.vincentf13.exchange.admin.infra.persistence.repository.InstrumentRepository;
import open.vincentf13.exchange.admin.contract.dto.InstrumentDetailResponse;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;
import open.vincentf13.sdk.core.OpenObjectMapper;
import open.vincentf13.sdk.core.exception.OpenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InstrumentQueryService {

    private final InstrumentRepository instrumentRepository;

    @Transactional(readOnly = true)
    public List<InstrumentSummaryResponse> list(InstrumentStatus status, InstrumentType instrumentType) {
        List<Instrument> instruments = instrumentRepository.findAll(status, instrumentType);
        return OpenObjectMapper.convertList(instruments, InstrumentSummaryResponse.class);
    }

    @Transactional(readOnly = true)
    public InstrumentDetailResponse get(Long instrumentId) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> OpenException.of(AdminErrorCodeEnum.INSTRUMENT_NOT_FOUND,
                                                    Map.of("instrumentId", instrumentId)));
        return OpenObjectMapper.convert(instrument, InstrumentDetailResponse.class);
    }
}
