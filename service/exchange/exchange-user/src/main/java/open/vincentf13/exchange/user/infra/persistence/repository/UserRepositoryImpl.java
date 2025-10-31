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
    public Optional<User> findById(Long id) {
        UserPO probe = UserPO.builder().id(id).build();
        return mapper.findByPO(probe).stream()
                .findFirst()
                .map(po -> OpenMapstruct.map(po, User.class));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        UserPO probe = UserPO.builder().email(email).build();
        return mapper.findByPO(probe).stream()
                .findFirst()
                .map(po -> OpenMapstruct.map(po, User.class));
    }

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
