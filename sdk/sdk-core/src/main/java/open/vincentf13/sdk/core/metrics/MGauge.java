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
     *   <li><b>executor</b> (Timer): 任務執行耗時情況。
     *     <ul>
     *       <li>count: 已執行任務的總次數</li>
     *       <li>sum: 所有任務執行的總耗時</li>
     *       <li>max: 近期單次任務執行的最大耗時</li>
     *     </ul>
     *   </li>
     *   <li><b>executor.idle</b> (Timer): 執行緒閒置時間。
     *     <ul>
     *       <li>count: 閒置事件的總次數</li>
     *       <li>sum: 所有執行緒的總閒置時間</li>
     *       <li>max: 近期單次閒置的最長時間</li>
     *     </ul>
     *   </li>
     * </ul>
     * <p>
     * <b>注意：指標生效條件</b>
     * <ul>
     *   <li>
     *       <b>Gauge 指標 (active, queued, pool.*):</b> 傳入的 {@code ex} 必須是 {@link java.util.concurrent.ThreadPoolExecutor}
     *       或 {@link java.util.concurrent.ForkJoinPool} 的實例。若使用 {@code Executors.newSingleThreadExecutor()}
     *       (回傳 Wrapper) 或其他包裝類，Micrometer 將無法讀取內部狀態，導致這些指標消失。
     *   </li>
     *   <li>
     *       <b>Timer 指標 (executor, executor.idle):</b> 必須使用此方法<b>回傳的 ExecutorService</b> 來執行任務，
     *       才能啟用計時與閒置時間統計。若繼續使用原本傳入的 {@code ex}，這些指標將不會更新。
     *   </li>
     * </ul>
     *
     * @param metric 指標定義 (Enum)，其名稱將作為指標前綴
     * @param ex     要監控的 ExecutorService
     * @param tags   標籤
     * @return 經過 Micrometer 包裝後的 ExecutorService，請使用此物件來執行任務以啟用完整監控
     */
    public static ExecutorService monitorExecutor(IMetric metric, ExecutorService ex, String name, String... tags) {
        return ExecutorServiceMetrics.monitor(Metrics.getRegistry(), ex, name, Tags.of(tags));
    }
}
