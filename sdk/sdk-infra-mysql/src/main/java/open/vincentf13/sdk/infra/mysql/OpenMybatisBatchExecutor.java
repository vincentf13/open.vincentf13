package open.vincentf13.sdk.infra.mysql;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/*
  提供 MyBatis Batch Executor 工具。
 */
public final class OpenMybatisBatchExecutor {

    public static final int DEFAULT_FLUSH_THRESHOLD = 1000;

    private static volatile SqlSessionFactory sqlSessionFactory;

    private OpenMybatisBatchExecutor() {
    }

    public static void register(SqlSessionFactory factory) {
        Objects.requireNonNull(factory, "sqlSessionFactory");
        sqlSessionFactory = factory;
    }

    public static <Mapper, Record> void execute(Class<Mapper> mapperType,
                                                List<Record> records,
                                                BiConsumer<Mapper, Record> action) {
        execute(mapperType, records, action, DEFAULT_FLUSH_THRESHOLD);
    }

    public static <Mapper, Record> void execute(Class<Mapper> mapperType,
                                                List<Record> records,
                                                BiConsumer<Mapper, Record> action,
                                                int flushThreshold) {
        Objects.requireNonNull(sqlSessionFactory, "sqlSessionFactory");
        Objects.requireNonNull(mapperType, "mapperType");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(action, "action");

        SqlSession sqlSession = SqlSessionUtils.getSqlSession(sqlSessionFactory, ExecutorType.BATCH, null);
        try {
            Mapper mapper = sqlSession.getMapper(mapperType);
            for (int i = 0; i < records.size(); i++) {
                action.accept(mapper, records.get(i));
                if ((i + 1) % flushThreshold == 0) {
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
