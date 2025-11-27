package open.vincentf13.exchange.user.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import open.vincentf13.exchange.user.infra.persistence.po.AuthCredentialPendingPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthCredentialPendingMapper extends BaseMapper<AuthCredentialPendingPO> {
}
