package open.vincentf13.exchange.user.infra.persistence.mapper;

import open.vincentf13.exchange.user.domain.model.UserAggregate;
import open.vincentf13.exchange.user.domain.model.UserStatus;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    boolean existsByEmail(@Param("email") String email);

    void insert(UserAggregate user);

    UserAggregate findById(@Param("id") Long id);

    UserAggregate findByEmail(@Param("email") String email);

    void updateStatus(@Param("id") Long id, @Param("status") UserStatus status);

    List<UserAggregate> findAll();
}
