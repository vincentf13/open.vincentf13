package open.vincentf13.sdk.leetcode.easy.sliding_window;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaxAverageSubarrayITest {
    
    @Test
    @DisplayName("測試最大平均子陣列案例")
    void testFindMaxAverage() {
        /**
         LeetCode 官方用例 1
         輸入: nums = [1,12,-5,-6,50,3], k = 4
         輸出: 12.75
         */
        assertEquals(12.75, MaxAverageSubarrayI.findMaxAverage(new int[]{1, 12, -5, -6, 50, 3}, 4), 1e-5);
        
        /**
         LeetCode 官方用例 2
         輸入: nums = [5], k = 1
         輸出: 5.0
         */
        assertEquals(5.0, MaxAverageSubarrayI.findMaxAverage(new int[]{5}, 1), 1e-5);
        
        /**
         自訂用例: 全部為負數
         輸入: nums = [-1, -12, -5, -6, -50, -3], k = 4
         輸出: -6.0 ((-1-12-5-6)/4 = -24/4 = -6.0)
         */
        assertEquals(-6.0, MaxAverageSubarrayI.findMaxAverage(new int[]{-1, -12, -5, -6, -50, -3}, 4), 1e-5);
        
        /**
         自訂用例: k 等於陣列長度
         輸入: nums = [1, 2, 3], k = 3
         輸出: 2.0
         */
        assertEquals(2.0, MaxAverageSubarrayI.findMaxAverage(new int[]{1, 2, 3}, 3), 1e-5);
    }
}
