package open.vincentf13.exchange.user.infra.persistence.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.common.core.OpenMapstruct;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.infra.persistence.mapper.UserMapper;
import open.vincentf13.exchange.user.infra.persistence.po.UserPO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper mapper;

    @Override
    public boolean existsByEmail(String email) {
        return mapper.existsByEmail(email);
    }

    @Override
    public void insert(User user) {
        mapper.insert(OpenMapstruct.map(user, UserPO.class));
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(mapper.findById(id))
                .map(po -> OpenMapstruct.map(po, User.class));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(mapper.findByEmail(email))
                .map(po -> OpenMapstruct.map(po, User.class));
    }

    @Override
    public void updateSelective(User user) {
        mapper.updateSelective(OpenMapstruct.map(user, UserPO.class));
    }
}
