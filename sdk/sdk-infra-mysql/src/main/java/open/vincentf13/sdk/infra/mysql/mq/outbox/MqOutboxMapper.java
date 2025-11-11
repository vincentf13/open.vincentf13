package open.vincentf13.sdk.infra.mysql.mq.outbox;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface MqOutboxMapper {

    void insertSelective(MqOutboxPO record);

    List<MqOutboxPO> findByPO(MqOutboxPO criteria);

    void updateSelective(MqOutboxPO record);

    void deleteByEventId(@Param("eventId") String eventId);

    void batchInsert(@Param("list") List<MqOutboxPO> records);

    void batchUpdate(@Param("list") List<MqOutboxPO> records);
}
