package open.vincentf13.sdk.infra.mysql.mybatis;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.infra.mysql.OpenMybatisBatchExecutor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenMybatisBatchExecutorRegistrar {

    private final SqlSessionFactory sqlSessionFactory;

    @PostConstruct
    public void register() {
        OpenMybatisBatchExecutor.register(sqlSessionFactory);
    }
}
