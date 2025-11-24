package open.vincentf13.exchange.user.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.OpenMapstruct;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.infra.persistence.mapper.UserMapper;
import open.vincentf13.exchange.user.infra.persistence.po.UserPO;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Validated
public class UserRepository {

    private final UserMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public void insertSelective(@NotNull @Valid User user) {
        user.setId(idGenerator.newLong());
        UserPO po = OpenMapstruct.map(user, UserPO.class);
        mapper.insertSelective(po);
    }

    public void updateSelective(@NotNull @Valid User user) {
        mapper.updateSelective(OpenMapstruct.map(user, UserPO.class));
    }

    public List<User> findBy(@NotNull @Valid User probe) {
        UserPO poProbe = OpenMapstruct.map(probe, UserPO.class);
        return mapper.findBy(poProbe).stream()
                .map(item -> OpenMapstruct.map(item, User.class))
                .collect(Collectors.toList());
    }

    public Optional<User> findOne(@NotNull @Valid User probe) {
        List<User> results = findBy(probe);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single user but found " + results.size());
        }
        return Optional.of(results.get(0));
    }

    public void batchInsert(@NotEmpty List<@Valid User> users) {
        users.forEach(user -> user.setId(idGenerator.newLong()));
        mapper.batchInsert(users.stream()
                .map(user -> OpenMapstruct.map(user, UserPO.class))
                .collect(Collectors.toList()));
    }

    public void batchUpdate(@NotEmpty List<@Valid User> users) {
        mapper.batchUpdate(users.stream()
                .map(user -> OpenMapstruct.map(user, UserPO.class))
                .collect(Collectors.toList()));
    }
}
