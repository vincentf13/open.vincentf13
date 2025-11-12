package open.vincentf13.exchange.order.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.OrderEvent;
import open.vincentf13.exchange.order.infra.persistence.mapper.OrderEventMapper;
import open.vincentf13.exchange.order.infra.persistence.po.OrderEventPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderEventRepositoryImpl implements OrderEventRepository {

    private final OrderEventMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public void append(OrderEvent event) {
        event.setEventId(idGenerator.newLong());
        OrderEventPO po = OpenMapstruct.map(event, OrderEventPO.class);
        mapper.insertWithSequence(po);
    }
}
