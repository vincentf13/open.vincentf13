package open.vincentf13.sdk.leetcode.easy.hash_map_set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniqueOccurrencesTest {

    @Test
    @DisplayName("測試獨一無二出現次數案例")
    void testUniqueOccurrences() {
        /** 
         LeetCode 官方用例 1
         輸入: arr = [1,2,2,1,1,3]
         次數: 1->3, 2->2, 3->1 (3, 2, 1)
         輸出: true
         */
        assertTrue(UniqueOccurrences.uniqueOccurrences(new int[]{1, 2, 2, 1, 1, 3}));

        /** 
         LeetCode 官方用例 2
         輸入: arr = [1,2]
         次數: 1->1, 2->1 (1, 1)
         輸出: false
         */
        assertFalse(UniqueOccurrences.uniqueOccurrences(new int[]{1, 2}));

        /** 
         LeetCode 官方用例 3
         輸入: arr = [-3,0,1,-3,1,1,1,-3,10,0]
         次數: -3->3, 0->2, 1->4, 10->1 (3, 2, 4, 1)
         輸出: true
         */
        assertTrue(UniqueOccurrences.uniqueOccurrences(new int[]{-3, 0, 1, -3, 1, 1, 1, -3, 10, 0}));

        /** 
         自訂用例: 只有一個元素
         */
        assertTrue(UniqueOccurrences.uniqueOccurrences(new int[]{42}));

        /** 
         自訂用例: 多種相同次數
         [1, 1, 2, 2, 3, 3] -> 次數都是 2
         輸出: false
         */
        assertFalse(UniqueOccurrences.uniqueOccurrences(new int[]{1, 1, 2, 2, 3, 3}));
    }
}
