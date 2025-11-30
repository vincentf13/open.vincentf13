package open.vincentf13.exchange.risk.infra.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.infra.persistence.mapper.RiskLimitMapper;
import open.vincentf13.exchange.risk.infra.persistence.po.RiskLimitPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class RiskLimitRepository {

    private final RiskLimitMapper mapper;

    public Optional<RiskLimit> findByInstrumentId(@NotNull Long instrumentId) {
        RiskLimitPO po = mapper.selectOne(
                Wrappers.lambdaQuery(RiskLimitPO.class)
                        .eq(RiskLimitPO::getInstrumentId, instrumentId)
                        .eq(RiskLimitPO::getIsActive, true)
        );
        return Optional.ofNullable(OpenObjectMapper.convert(po, RiskLimit.class));
    }
}
