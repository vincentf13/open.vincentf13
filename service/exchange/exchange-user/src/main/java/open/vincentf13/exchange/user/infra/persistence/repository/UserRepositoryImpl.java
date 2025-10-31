package open.vincentf13.exchange.user.infra.persistence.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.common.core.OpenMapstruct;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.infra.persistence.mapper.UserMapper;
import open.vincentf13.exchange.user.infra.persistence.po.UserPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper mapper;

    @Override
    public boolean existsByEmail(String email) {
        return mapper.existsByEmail(email);
    }

    @Override
    public void insertSelective(User user) {
        mapper.insertSelective(OpenMapstruct.map(user, UserPO.class));
    }

    @Override
    @Override
    public void updateSelective(User user) {
        mapper.updateSelective(OpenMapstruct.map(user, UserPO.class));
    }

    @Override
    public List<User> findBy(User probe) {
        UserPO poProbe = OpenMapstruct.map(probe, UserPO.class);
        return mapper.findByPO(poProbe).stream()
                .map(item -> OpenMapstruct.map(item, User.class))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<User> findOne(User probe) {
        List<User> results = findBy(probe);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single user but found " + results.size());
        }
        return Optional.of(results.get(0));
    }

    @Override
    public void batchInsert(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        mapper.batchInsert(users.stream()
                .map(user -> OpenMapstruct.map(user, UserPO.class))
                .collect(Collectors.toList()));
    }

    @Override
    public void batchUpdate(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        mapper.batchUpdate(users.stream()
                .map(user -> OpenMapstruct.map(user, UserPO.class))
                .collect(Collectors.toList()));
    }
}
