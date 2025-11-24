package open.vincentf13.exchange.marketdata.infra.persistence.mapper;

import open.vincentf13.exchange.marketdata.infra.persistence.po.KlineBucketPO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface KlineBucketMapper {

    void insertSelective(KlineBucketPO record);

    int updateSelectiveBy(@Param("record") KlineBucketPO record,
                          @Param("bucketId") Long bucketId,
                          @Param("instrumentId") Long instrumentId,
                          @Param("period") String period,
                          @Param("closed") Boolean closed);

    KlineBucketPO findByInstrumentPeriodAndStart(@Param("instrumentId") Long instrumentId,
                                                 @Param("period") String period,
                                                 @Param("bucketStart") Instant bucketStart);

    List<KlineBucketPO> findBucketsBetween(@Param("instrumentId") Long instrumentId,
                                           @Param("period") String period,
                                           @Param("start") Instant start,
                                           @Param("end") Instant end);

    KlineBucketPO findLatestBefore(@Param("instrumentId") Long instrumentId,
                                   @Param("period") String period,
                                   @Param("start") Instant start);
}
