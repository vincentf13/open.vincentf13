package open.vincentf13.sdk.infra.mysql.mq.offset;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MqConsumerOffsetMapper {
    
    void insertSelective(MqConsumerOffsetPO record);
    
    List<MqConsumerOffsetPO> findBy(MqConsumerOffsetPO criteria);
    
    void updateSelective(MqConsumerOffsetPO record);
    
    void batchUpsert(@Param("list") List<MqConsumerOffsetPO> records);
}
