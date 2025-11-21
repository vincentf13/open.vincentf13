package open.vincentf13.sdk.infra.mysql.mq.outbox;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MqOutboxMapper {

    void insertSelective(MqOutboxPO record);

    void insertWithAutoSeq(MqOutboxPO record);

    List<MqOutboxPO> findBy(MqOutboxPO criteria);

    void updateSelective(MqOutboxPO record);

    void deleteByEventId(@Param("eventId") String eventId);

    void batchInsert(@Param("list") List<MqOutboxPO> records);

    void batchUpdate(@Param("list") List<MqOutboxPO> records);


}
