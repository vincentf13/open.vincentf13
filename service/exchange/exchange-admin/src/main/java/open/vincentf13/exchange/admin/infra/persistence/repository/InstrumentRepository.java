package open.vincentf13.exchange.admin.infra.persistence.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.domain.model.Instrument;
import open.vincentf13.exchange.admin.infra.persistence.mapper.InstrumentMapper;
import open.vincentf13.exchange.admin.infra.persistence.po.InstrumentPO;
import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;
import open.vincentf13.sdk.core.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class InstrumentRepository {

    private final InstrumentMapper instrumentMapper;

    public List<Instrument> findAll(InstrumentStatus status, InstrumentType instrumentType) {
        InstrumentPO condition = InstrumentPO.builder()
                .status(status)
                .instrumentType(instrumentType)
                .build();
        List<InstrumentPO> records = instrumentMapper.findBy(condition);
        return OpenObjectMapper.convertList(records, Instrument.class);
    }

    public Optional<Instrument> findById(@NotNull Long instrumentId) {
        InstrumentPO condition = InstrumentPO.builder().instrumentId(instrumentId).build();
        InstrumentPO po = instrumentMapper.findOne(condition);
        return Optional.ofNullable(OpenObjectMapper.convert(po, Instrument.class));
    }
}
