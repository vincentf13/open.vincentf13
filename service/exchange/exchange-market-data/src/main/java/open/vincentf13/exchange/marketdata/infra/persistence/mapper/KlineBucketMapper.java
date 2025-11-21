package open.vincentf13.exchange.marketdata.infra.persistence.mapper;

import open.vincentf13.exchange.marketdata.infra.persistence.po.KlineBucketPO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface KlineBucketMapper {

    void insertSelective(KlineBucketPO record);

    int updateById(KlineBucketPO record);

    KlineBucketPO findByInstrumentPeriodAndStart(@Param("instrumentId") Long instrumentId,
                                                 @Param("period") String period,
                                                 @Param("bucketStart") Instant bucketStart);

    KlineBucketPO findActiveBucket(@Param("instrumentId") Long instrumentId,
                                   @Param("period") String period,
                                   @Param("targetTime") Instant targetTime);

    List<KlineBucketPO> findRecentBuckets(@Param("instrumentId") Long instrumentId,
                                          @Param("period") String period,
                                          @Param("limit") int limit);

    List<KlineBucketPO> findBucketsBetween(@Param("instrumentId") Long instrumentId,
                                           @Param("period") String period,
                                           @Param("start") Instant start,
                                           @Param("end") Instant end);

    KlineBucketPO findLatestBefore(@Param("instrumentId") Long instrumentId,
                                   @Param("period") String period,
                                   @Param("start") Instant start);
}
