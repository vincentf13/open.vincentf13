package open.vincentf13.exchange.user.infra.repository;

import open.vincentf13.exchange.user.domain.User;
import open.vincentf13.exchange.user.domain.UserStatus;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    boolean existsByEmail(String email);

    void insert(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    void updateStatus(Long id, UserStatus status);

    List<User> findAll();
}
