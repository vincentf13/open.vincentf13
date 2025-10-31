package open.vincentf13.exchange.user.infra.persistence.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.user.domain.model.UserAggregate;
import open.vincentf13.exchange.user.domain.model.UserStatus;
import open.vincentf13.exchange.user.infra.persistence.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserMyBatisRepository implements UserRepository {

    private final UserMapper mapper;

    @Override
    public boolean existsByEmail(String email) {
        return mapper.existsByEmail(email);
    }

    @Override
    public void insert(UserAggregate user) {
        mapper.insert(user);
    }

    @Override
    public Optional<UserAggregate> findById(Long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public Optional<UserAggregate> findByEmail(String email) {
        return Optional.ofNullable(mapper.findByEmail(email));
    }

    @Override
    public void updateStatus(Long id, UserStatus status) {
        mapper.updateStatus(id, status);
    }

    @Override
    public List<UserAggregate> findAll() {
        return mapper.findAll();
    }
}
