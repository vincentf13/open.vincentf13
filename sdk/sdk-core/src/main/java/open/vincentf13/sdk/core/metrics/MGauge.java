package open.vincentf13.sdk.core.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import open.vincentf13.sdk.core.metrics.enums.IMetric;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 負責數值監測 (Gauge) 與執行緒池監控相關的操作。
 * <p>
 * 使用 Gauge 可以取得即時的數值狀態，例如當前佇列長度、連線數等。
 * 本類別也提供執行緒池監控，可取得 active, queued, pool.size 等多項指標。
 */
public final class MGauge {

    private MGauge() {}

    /**
     * 註冊一個基於 AtomicLong 的 Gauge。
     *
     * @param metric 指標定義 (Enum)
     * @param ref    要監測的 AtomicLong 物件
     * @param tags   標籤
     * @return 傳入的 AtomicLong 物件，方便鏈式調用
     */
    public static AtomicLong register(IMetric metric, AtomicLong ref, String... tags) {
        MetricValidator.validate(metric, tags);
        Gauge.builder(metric.getName(), ref, AtomicLong::get).tags(tags).register(Metrics.getRegistry());
        return ref;
    }

    /**
     * 監控 ExecutorService (執行緒池) 的狀態。
     * <p>
     * 綁定後可取得以下指標 (以 name 作為前綴):
     * <ul>
     *   <li><b>executor.active</b> (Gauge): 正在執行任務的執行緒數</li>
     *   <li><b>executor.queued</b> (Gauge): 佇列中等待執行的任務數</li>
     *   <li><b>executor.pool.size</b> (Gauge): 當前池中的執行緒數</li>
     *   <li><b>executor.pool.core</b> (Gauge): 核心執行緒數</li>
     *   <li><b>executor.pool.max</b> (Gauge): 最大允許執行緒數</li>
     *   <li><b>executor.completed</b> (FunctionCounter): 已完成的任務總數</li>
     *   <li><b>executor.seconds.sum</b> (Timer): 任務執行的總耗時</li>
     *   <li><b>executor.seconds.count</b> (Timer): 任務執行的總次數</li>
     *   <li><b>executor.idle</b> (Gauge): 閒置中的執行緒數</li>
     * </ul>
     *
     * @param metric 指標定義 (Enum)，其名稱將作為指標前綴
     * @param ex     要監控的 ExecutorService
     * @param tags   標籤
     */
    public static void monitorExecutor(IMetric metric, ExecutorService ex, String... tags) {
        MetricValidator.validate(metric, tags);
        ExecutorServiceMetrics.monitor(Metrics.getRegistry(), ex, metric.getName(), Tags.of(tags));
    }
}
