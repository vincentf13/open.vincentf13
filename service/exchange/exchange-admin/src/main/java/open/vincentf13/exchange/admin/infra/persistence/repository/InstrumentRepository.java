package open.vincentf13.exchange.admin.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.domain.model.Instrument;
import open.vincentf13.exchange.admin.infra.persistence.mapper.InstrumentMapper;
import open.vincentf13.exchange.admin.infra.persistence.po.InstrumentPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class InstrumentRepository {

    private final InstrumentMapper instrumentMapper;

    public List<Instrument> findBy(@NotNull LambdaQueryWrapper<InstrumentPO> wrapper) {
        return OpenObjectMapper.convertList(instrumentMapper.selectList(wrapper), Instrument.class);
    }

    public Optional<Instrument> findOne(@NotNull LambdaQueryWrapper<InstrumentPO> wrapper) {
        InstrumentPO po = instrumentMapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, Instrument.class));
    }
}
