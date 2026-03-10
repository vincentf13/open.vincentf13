package open.vincentf13.service.spot.infra.worker;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/** 
  高性能忙等任務基類
  統一管理單執行緒輪詢與資源生命週期
 */
public abstract class BusySpinWorker implements Runnable {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected IdleStrategy idleStrategy = new BackoffIdleStrategy();

    public void setIdleStrategy(IdleStrategy strategy) {
        this.idleStrategy = strategy;
    }

    public void start(String threadName) {
        if (running.compareAndSet(false, true)) {
            Thread thread = new Thread(this, threadName);
            thread.setDaemon(true);
            thread.start();
            log.info("Worker started: {}", threadName);
        }
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void run() {
        try {
            onStart();
            while (running.get()) {
                int workDone = doWork();
                idleStrategy.idle(workDone);
            }
        } catch (Exception e) {
            log.error("Worker error: {}", e.getMessage(), e);
        } finally {
            onStop();
        }
    }

    protected abstract void onStart();
    protected abstract int doWork() throws Exception;
    protected abstract void onStop();
}
