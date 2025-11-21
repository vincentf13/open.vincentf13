package open.vincentf13.sdk.infra.mysql.mq.deadletter;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface MqDeadLetterMapper {

    void insertSelective(MqDeadLetterPO record);

    List<MqDeadLetterPO> findBy(MqDeadLetterPO criteria);

    void updateSelective(MqDeadLetterPO record);

    void batchInsert(@Param("list") List<MqDeadLetterPO> records);

    void batchUpdate(@Param("list") List<MqDeadLetterPO> records);
}
