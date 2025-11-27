package open.vincentf13.exchange.marketdata.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.marketdata.domain.model.MarkPriceSnapshot;
import open.vincentf13.exchange.marketdata.infra.persistence.mapper.MarkPriceSnapshotMapper;
import open.vincentf13.exchange.marketdata.infra.persistence.po.MarkPriceSnapshotPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class MarkPriceSnapshotRepository {

    private final MarkPriceSnapshotMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public MarkPriceSnapshot insertSelective(@NotNull @Valid MarkPriceSnapshot snapshot) {
        MarkPriceSnapshotPO record = OpenObjectMapper.convert(snapshot, MarkPriceSnapshotPO.class);
        if (record.getSnapshotId() == null) {
            record.setSnapshotId(idGenerator.newLong());
        }
        if (record.getCalculatedAt() == null) {
            record.setCalculatedAt(Instant.now());
        }
        mapper.insert(record);
        snapshot.setSnapshotId(record.getSnapshotId());
        snapshot.setCalculatedAt(record.getCalculatedAt());
        return snapshot;
    }

    public Optional<MarkPriceSnapshot> findLatest(@NotNull Long instrumentId) {
        LambdaQueryWrapper<MarkPriceSnapshotPO> wrapper = Wrappers.lambdaQuery(MarkPriceSnapshotPO.class)
                .eq(MarkPriceSnapshotPO::getInstrumentId, instrumentId)
                .orderByDesc(MarkPriceSnapshotPO::getCalculatedAt)
                .last("LIMIT 1");
        MarkPriceSnapshotPO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(po).map(value -> OpenObjectMapper.convert(value, MarkPriceSnapshot.class));
    }
}
