package open.vincentf13.exchange.order.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.persistence.mapper.OrderMapper;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.core.validator.Id;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class OrderRepository {
    
    private final OrderMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public void insertSelective(@NotNull @Valid Order order) {
        if (order.getOrderId() == null) {
            order.setOrderId(idGenerator.newLong());
        }
        OrderPO po = OpenObjectMapper.convert(order, OrderPO.class);
        mapper.insert(po);
    }
    
    public List<Order> findBy(@NotNull LambdaQueryWrapper<OrderPO> wrapper) {
        return OpenObjectMapper.convertList(mapper.selectList(wrapper), Order.class);
    }
    
    public Optional<Order> findOne(@NotNull LambdaQueryWrapper<OrderPO> wrapper) {
        OrderPO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, Order.class));
    }
    
    @Validated({Default.class, Id.class})
    public boolean updateSelective(@NotNull @Valid Order update,
                                   @NotNull LambdaUpdateWrapper<OrderPO> updateWrapper) {
        OrderPO record = OpenObjectMapper.convert(update, OrderPO.class);
        return mapper.update(record, updateWrapper) > 0;
    }
}
