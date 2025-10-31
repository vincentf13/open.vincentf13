package open.vincentf13.exchange.user.infra.persistence.repository;

import open.vincentf13.exchange.user.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    boolean existsByEmail(String email);

    void insertSelective(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    void updateSelective(User user);

    List<User> findBy(User probe);

    void batchInsert(List<User> users);

    void batchUpdate(List<User> users);
}
