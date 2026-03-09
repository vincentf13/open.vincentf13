package open.vincentf13.sdk.leetcode.easy.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentCounterTest {

    @Test
    @DisplayName("測試最近請求計數器案例")
    void testRecentCounter() {
        /** 
         LeetCode 官方用例
         輸入: ["RecentCounter", "ping", "ping", "ping", "ping"]
               [[], [1], [100], [3001], [3002]]
         輸出: [null, 1, 2, 3, 3]
         */
        RecentCounter counter = new RecentCounter();
        
        // ping(1) -> [1] -> 返回 1
        assertEquals(1, counter.ping(1));
        
        // ping(100) -> [1, 100] -> 返回 2
        assertEquals(2, counter.ping(100));
        
        // ping(3001) -> [1, 100, 3001] -> 返回 3
        assertEquals(3, counter.ping(3001));
        
        // ping(3002) -> [100, 3001, 3002] (1 過期) -> 返回 3
        assertEquals(3, counter.ping(3002));
        
        /** 
         自訂用例: 剛好在邊界 3000ms
         輸入: ping(6002) -> [3002, 6002] (100, 3001 已過期) -> 返回 2
         */
        assertEquals(2, counter.ping(6002));
        
        /** 
         自訂用例: 大量過期
         輸入: ping(10000) -> [10000] (之前全部過期) -> 返回 1
         */
        assertEquals(1, counter.ping(10000));
    }
}
