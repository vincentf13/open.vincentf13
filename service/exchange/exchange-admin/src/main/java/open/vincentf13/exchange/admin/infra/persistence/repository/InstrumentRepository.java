package open.vincentf13.exchange.admin.infra.persistence.repository;

import open.vincentf13.exchange.admin.domain.model.Instrument;
import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository {

    List<Instrument> findAll(InstrumentStatus status, InstrumentType instrumentType);

    Optional<Instrument> findById(Long instrumentId);
}
