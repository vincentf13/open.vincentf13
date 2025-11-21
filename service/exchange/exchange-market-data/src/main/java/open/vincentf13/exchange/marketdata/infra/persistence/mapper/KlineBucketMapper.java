package open.vincentf13.exchange.marketdata.infra.persistence.mapper;

import open.vincentf13.exchange.marketdata.infra.persistence.po.KlineBucketPO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

public interface KlineBucketMapper {

    void insertSelective(KlineBucketPO record);

    int updateById(KlineBucketPO record);

    KlineBucketPO findByInstrumentPeriodAndStart(@Param("instrumentId") Long instrumentId,
                                                 @Param("period") String period,
                                                 @Param("bucketStart") Instant bucketStart);

    KlineBucketPO findActiveBucket(@Param("instrumentId") Long instrumentId,
                                   @Param("period") String period,
                                   @Param("targetTime") Instant targetTime);
}
