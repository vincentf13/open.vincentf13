package open.vincentf13.sdk.infra.mysql;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "OpenMybatisBatchExecutor must not run within an active transaction");
    }
    SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH, false);
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
      sqlSession.commit();
    } catch (Exception e) {
      sqlSession.rollback();
      throw e;
    } finally {
      sqlSession.close();
    }
  }
}
