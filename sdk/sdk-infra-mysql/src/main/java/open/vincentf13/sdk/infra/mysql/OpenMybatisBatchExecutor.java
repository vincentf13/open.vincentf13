package open.vincentf13.sdk.infra.mysql;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionUtils;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BiConsumer;

public class OpenMybatisBatchExecutor {

    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final SqlSessionFactory sqlSessionFactory;

    public OpenMybatisBatchExecutor(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = Objects.requireNonNull(sqlSessionFactory, "sqlSessionFactory");
    }

    public <T> void execute(Collection<T> items, BiConsumer<SqlSession, T> consumer) {
        if (items == null || items.isEmpty()) {
            return;
        }
        SqlSession sqlSession = SqlSessionUtils.getSqlSession(sqlSessionFactory, ExecutorType.BATCH, null);
        try {
            int count = 0;
            for (T item : items) {
                consumer.accept(sqlSession, item);
                count++;
                if (count % DEFAULT_BATCH_SIZE == 0) {
                    sqlSession.flushStatements();
                    sqlSession.clearCache();
                }
            }
            sqlSession.flushStatements();
            sqlSession.clearCache();
        } finally {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
        }
    }
}
