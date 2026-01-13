package open.vincentf13.exchange.user.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.infra.persistence.mapper.UserMapper;
import open.vincentf13.exchange.user.infra.persistence.po.UserPO;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Repository
@RequiredArgsConstructor
@Validated
public class UserRepository {

  private final UserMapper mapper;
  private final DefaultIdGenerator idGenerator;

  public void insertSelective(@NotNull @Valid User user) {
    if (user.getId() == null) {
      user.setId(idGenerator.newLong());
    }
    UserPO po = OpenObjectMapper.convert(user, UserPO.class);
    mapper.insert(po);
  }

  public Optional<User> findOne(@NotNull LambdaQueryWrapper<UserPO> wrapper) {
    UserPO po = mapper.selectOne(wrapper);
    return Optional.ofNullable(OpenObjectMapper.convert(po, User.class));
  }
}
