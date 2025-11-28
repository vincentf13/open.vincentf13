package open.vincentf13.exchange.user.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.user.domain.model.AuthCredentialPending;
import open.vincentf13.exchange.user.infra.persistence.mapper.AuthCredentialPendingMapper;
import open.vincentf13.exchange.user.infra.persistence.po.AuthCredentialPendingPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Validated
public class AuthCredentialPendingRepository {
    
    private final AuthCredentialPendingMapper mapper;
    
    public void insert(@NotNull AuthCredentialPending credential) {
        mapper.insert(OpenObjectMapper.convert(credential, AuthCredentialPendingPO.class));
    }
    
    public boolean update(@NotNull AuthCredentialPending update,
                          @NotNull LambdaUpdateWrapper<AuthCredentialPendingPO> updateWrapper) {
        AuthCredentialPendingPO record = OpenObjectMapper.convert(update, AuthCredentialPendingPO.class);
        return mapper.update(record, updateWrapper) > 0;
    }
    
    public List<AuthCredentialPending> findBy(@NotNull LambdaQueryWrapper<AuthCredentialPendingPO> wrapper) {
        return mapper.selectList(wrapper).stream()
                     .map(item -> OpenObjectMapper.convert(item, AuthCredentialPending.class))
                     .collect(Collectors.toList());
    }
}
