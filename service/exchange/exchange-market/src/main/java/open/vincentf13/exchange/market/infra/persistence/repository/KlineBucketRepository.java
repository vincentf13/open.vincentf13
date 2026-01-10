package open.vincentf13.exchange.market.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.domain.model.KlineBucket;
import open.vincentf13.exchange.market.infra.persistence.mapper.KlineBucketMapper;
import open.vincentf13.exchange.market.infra.persistence.po.KlineBucketPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.core.validator.UpdateAndDelete;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class KlineBucketRepository {
    
    private final KlineBucketMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public Optional<KlineBucket> findOne(@NotNull LambdaQueryWrapper<KlineBucketPO> wrapper) {
        KlineBucketPO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, KlineBucket.class));
    }
    
    public List<KlineBucket> findBy(@NotNull LambdaQueryWrapper<KlineBucketPO> wrapper) {
        return OpenObjectMapper.convertList(mapper.selectList(wrapper), KlineBucket.class);
    }
    
    public KlineBucket insertSelective(@NotNull @Valid KlineBucket bucket) {
        KlineBucketPO record = OpenObjectMapper.convert(bucket, KlineBucketPO.class);
        if (record.getIsClosed() == null) {
            record.setIsClosed(Boolean.FALSE);
        }
        if (record.getBucketId() == null) {
            record.setBucketId(idGenerator.newLong());
        }
        mapper.insert(record);
        bucket.setBucketId(record.getBucketId());
        bucket.setIsClosed(record.getIsClosed());
        return bucket;
    }
    
    @Validated({Default.class, UpdateAndDelete.class})
    public boolean updateSelectiveBy(@NotNull @Valid KlineBucket update,
                                     @NotNull LambdaUpdateWrapper<KlineBucketPO> updateWrapper) {
        KlineBucketPO record = OpenObjectMapper.convert(update, KlineBucketPO.class);
        return mapper.update(record, updateWrapper) > 0;
    }
}
