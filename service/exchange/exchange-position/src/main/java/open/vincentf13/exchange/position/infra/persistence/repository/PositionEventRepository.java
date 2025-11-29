package open.vincentf13.exchange.position.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.domain.model.PositionEvent;
import open.vincentf13.exchange.position.infra.persistence.mapper.PositionEventMapper;
import open.vincentf13.exchange.position.infra.persistence.po.PositionEventPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Repository
@RequiredArgsConstructor
@Validated
public class PositionEventRepository {

    private final PositionEventMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public void insert(@NotNull @Valid PositionEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(idGenerator.newLong());
        }
        PositionEventPO po = OpenObjectMapper.convert(event, PositionEventPO.class);
        mapper.insert(po);
    }
}
