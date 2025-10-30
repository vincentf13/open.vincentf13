package open.vincentf13.exchange.user.infra.mybatis.mapper;

import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.domain.model.UserStatus;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

public interface UserMapper {

    boolean existsByEmail(@Param("email") String email);

    void insert(User user);

    Optional<User> findById(@Param("id") Long id);

    Optional<User> findByEmail(@Param("email") String email);

    void updateStatus(@Param("id") Long id, @Param("status") UserStatus status);

    List<User> findAll();
}
