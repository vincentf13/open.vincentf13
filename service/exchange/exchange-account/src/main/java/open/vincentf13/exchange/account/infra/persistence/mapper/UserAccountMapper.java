package open.vincentf13.exchange.account.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import open.vincentf13.exchange.account.infra.persistence.po.UserAccountPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserAccountMapper extends BaseMapper<UserAccountPO> {

  @Update(
      "<script>"
          + "<foreach collection='list' item='item' separator=';'>"
          + "UPDATE user_accounts SET balance=#{item.po.balance}, available=#{item.po.available}, reserved=#{item.po.reserved}, version=#{item.po.version} "
          + "WHERE account_id=#{item.po.accountId} AND version=#{item.expectedVersion}"
          + "</foreach>"
          + "</script>")
  int batchUpdateWithOptimisticLock(@Param("list") List<?> list);
}
