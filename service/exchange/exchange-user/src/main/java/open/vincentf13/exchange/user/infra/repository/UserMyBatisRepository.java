package open.vincentf13.exchange.user.infra.repository;

import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.domain.model.UserStatus;
import open.vincentf13.exchange.user.domain.repository.UserRepository;
import open.vincentf13.exchange.user.infra.mybatis.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserMyBatisRepository implements UserRepository {

    private final UserMapper mapper;

    public UserMyBatisRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean existsByEmail(String email) {
        return mapper.existsByEmail(email);
    }

    @Override
    public void insert(User user) {
        mapper.insert(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return mapper.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return mapper.findByEmail(email);
    }

    @Override
    public void updateStatus(Long id, UserStatus status) {
        mapper.updateStatus(id, status);
    }

    @Override
    public List<User> findAll() {
        return mapper.findAll();
    }
}
