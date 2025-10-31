package open.vincentf13.exchange.user.infra.persistence.repository;

import open.vincentf13.exchange.user.domain.model.UserAggregate;
import open.vincentf13.exchange.user.domain.model.UserStatus;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    boolean existsByEmail(String email);

    void insert(UserAggregate user);

    Optional<UserAggregate> findById(Long id);

    Optional<UserAggregate> findByEmail(String email);

    void updateStatus(Long id, UserStatus status);

    List<UserAggregate> findAll();
}
