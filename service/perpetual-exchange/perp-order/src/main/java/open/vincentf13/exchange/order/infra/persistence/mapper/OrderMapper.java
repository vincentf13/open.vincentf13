package open.vincentf13.exchange.order.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<OrderPO> {
}
