package open.vincentf13.service.spot.infra;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Worker implements Runnable {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final IdleStrategy idleStrategy = new BackoffIdleStrategy(1, 1, 1, 1);
    private Thread thread;
    
    public void start(String name) {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, name);
            thread.start();
            log.info("Worker {} started", name);
        }
    }
    
    public void stop() {
        running.set(false);
        if (thread != null) {
            try {
                thread.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
        onStop();
    }
    
    @Override
    public void run() {
        onStart();
        while (running.get()) {
            int workDone = doWork();
            idleStrategy.idle(workDone);
        }
    }
    
    protected abstract void onStart();
    
    protected abstract int doWork();
    
    protected abstract void onStop();
}
