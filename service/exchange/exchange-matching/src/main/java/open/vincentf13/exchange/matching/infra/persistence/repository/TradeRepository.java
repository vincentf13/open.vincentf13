package open.vincentf13.exchange.matching.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.infra.persistence.mapper.TradeMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Repository
@Validated
@RequiredArgsConstructor
public class TradeRepository {
    
    private final TradeMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
}
