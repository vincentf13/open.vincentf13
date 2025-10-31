package open.vincentf13.exchange.user.infra.mybatis.mapper;

import open.vincentf13.exchange.user.domain.User;
import open.vincentf13.exchange.user.domain.UserStatus;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    boolean existsByEmail(@Param("email") String email);

    void insert(User user);

    User findById(@Param("id") Long id);

    User findByEmail(@Param("email") String email);

    void updateStatus(@Param("id") Long id, @Param("status") UserStatus status);

    List<User> findAll();
}
