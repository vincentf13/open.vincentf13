package open.vincentf13.sdk.leetcode.easy.prefix_sum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PivotIndexTest {
    
    @Test
    @DisplayName("測試尋找樞紐索引案例")
    void testPivotIndex() {
        /**
         LeetCode 官方用例 1
         輸入: nums = [1, 7, 3, 6, 5, 6]
         輸出: 3
         解釋: 索引 3 左側總和 11，右側總和 11。
         */
        assertEquals(3, PivotIndex.pivotIndex(new int[]{1, 7, 3, 6, 5, 6}));
        
        /**
         LeetCode 官方用例 2
         輸入: nums = [1, 2, 3]
         輸出: -1
         解釋: 陣列中不存在滿足條件的索引。
         */
        assertEquals(-1, PivotIndex.pivotIndex(new int[]{1, 2, 3}));
        
        /**
         LeetCode 官方用例 3
         輸入: nums = [2, 1, -1]
         輸出: 0
         解釋: 索引 0 左側總和 0，右側總和 1 + (-1) = 0。
         */
        assertEquals(0, PivotIndex.pivotIndex(new int[]{2, 1, -1}));
        
        /**
         自訂用例: 樞紐索引在最右側
         輸入: nums = [-1, 1, 0]
         輸出: 2
         解釋: 索引 2 左側總和 0，右側總和 0。
         */
        assertEquals(2, PivotIndex.pivotIndex(new int[]{-1, 1, 0}));
        
        /**
         自訂用例: 陣列只有一個元素
         輸入: nums = [10]
         輸出: 0
         解釋: 索引 0 左側總和 0，右側總和 0。
         */
        assertEquals(0, PivotIndex.pivotIndex(new int[]{10}));
        
        /**
         自訂用例: 含有多個可能的樞紐，返回最左側的
         輸入: nums = [0, 0, 0, 0]
         輸出: 0
         */
        assertEquals(0, PivotIndex.pivotIndex(new int[]{0, 0, 0, 0}));
    }
}
