package open.vincentf13.exchange.user.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import open.vincentf13.exchange.user.infra.persistence.po.UserPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserPO> {}
