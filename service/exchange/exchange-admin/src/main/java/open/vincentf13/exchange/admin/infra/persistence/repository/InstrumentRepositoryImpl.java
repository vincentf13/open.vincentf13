package open.vincentf13.exchange.admin.infra.persistence.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.domain.model.Instrument;
import open.vincentf13.exchange.admin.infra.persistence.mapper.InstrumentMapper;
import open.vincentf13.exchange.admin.infra.persistence.po.InstrumentPO;
import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InstrumentRepositoryImpl implements InstrumentRepository {

    private final InstrumentMapper instrumentMapper;

    @Override
    public List<Instrument> findAll(InstrumentStatus status, InstrumentType instrumentType) {
        InstrumentPO condition = InstrumentPO.builder()
                .status(status)
                .instrumentType(instrumentType)
                .build();
        List<InstrumentPO> records = instrumentMapper.findBy(condition);
        return OpenMapstruct.mapList(records, Instrument.class);
    }

    @Override
    public Optional<Instrument> findById(Long instrumentId) {
        InstrumentPO condition = InstrumentPO.builder().instrumentId(instrumentId).build();
        InstrumentPO po = instrumentMapper.findOne(condition);
        return Optional.ofNullable(OpenMapstruct.map(po, Instrument.class));
    }
}
