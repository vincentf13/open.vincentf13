package open.vincentf13.exchange.user.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.infra.persistence.mapper.UserMapper;
import open.vincentf13.exchange.user.infra.persistence.po.UserPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.infra.mysql.OpenMybatisBatchExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class UserRepository {

    private final UserMapper mapper;
    private final DefaultIdGenerator idGenerator;
    private final OpenMybatisBatchExecutor batchExecutor;

    public void insertSelective(@NotNull @Valid User user) {
        if (user.getId() == null) {
            user.setId(idGenerator.newLong());
        }
        UserPO po = OpenObjectMapper.convert(user, UserPO.class);
        mapper.insert(po);
    }

    public boolean updateSelective(@NotNull @Valid User update,
                                   @NotNull LambdaUpdateWrapper<UserPO> updateWrapper) {
        UserPO record = OpenObjectMapper.convert(update, UserPO.class);
        return mapper.update(record, updateWrapper) > 0;
    }

    public List<User> findBy(@NotNull LambdaQueryWrapper<UserPO> wrapper) {
        return OpenObjectMapper.convertList(mapper.selectList(wrapper), User.class);
    }

    public Optional<User> findOne(@NotNull LambdaQueryWrapper<UserPO> wrapper) {
        UserPO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, User.class));
    }

    public void batchInsert(@NotEmpty List<User> users) {
        users.forEach(user -> {
            if (user.getId() == null) {
                user.setId(idGenerator.newLong());
            }
        });
        List<UserPO> records = users.stream()
                .map(user -> OpenObjectMapper.convert(user, UserPO.class))
                .toList();
        batchExecutor.execute(UserMapper.class, records, UserMapper::insert);
    }

}
