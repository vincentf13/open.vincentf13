package open.vincentf13.exchange.user.infra.persistence.repository;

import open.vincentf13.exchange.user.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    void insertSelective(User user);

    void updateSelective(User user);

    List<User> findBy(User probe);

    void batchInsert(List<User> users);

    void batchUpdate(List<User> users);

    Optional<User> findOne(User probe);

    Optional<User> findOneForUpdate(User probe);
}
