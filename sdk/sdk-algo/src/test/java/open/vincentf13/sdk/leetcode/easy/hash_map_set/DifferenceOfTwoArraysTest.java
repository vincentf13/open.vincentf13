package open.vincentf13.sdk.leetcode.easy.hash_map_set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DifferenceOfTwoArraysTest {
    
    @Test
    @DisplayName("測試找出兩陣列差異案例")
    void testFindDifference() {
        /**
         LeetCode 官方用例 1
         輸入: nums1 = [1,2,3], nums2 = [2,4,6]
         輸出: [[1,3],[4,6]]
         */
        List<List<Integer>> result1 = DifferenceOfTwoArrays.findDifference(new int[]{1, 2, 3}, new int[]{2, 4, 6});
        assertEquals(2, result1.size());
        assertEquals(new HashSet<>(List.of(1, 3)), new HashSet<>(result1.get(0)));
        assertEquals(new HashSet<>(List.of(4, 6)), new HashSet<>(result1.get(1)));
        
        /**
         LeetCode 官方用例 2
         輸入: nums1 = [1,2,3,3], nums2 = [1,1,2,2]
         輸出: [[3],[]]
         */
        List<List<Integer>> result2 = DifferenceOfTwoArrays.findDifference(new int[]{1, 2, 3, 3}, new int[]{1, 1, 2, 2});
        assertEquals(2, result2.size());
        assertEquals(new HashSet<>(List.of(3)), new HashSet<>(result2.get(0)));
        assertEquals(new HashSet<>(), new HashSet<>(result2.get(1)));
        
        /**
         自訂用例: 兩陣列完全相同
         輸入: nums1 = [1, 2], nums2 = [2, 1]
         輸出: [[],[]]
         */
        List<List<Integer>> result3 = DifferenceOfTwoArrays.findDifference(new int[]{1, 2}, new int[]{2, 1});
        assertEquals(new HashSet<>(), new HashSet<>(result3.get(0)));
        assertEquals(new HashSet<>(), new HashSet<>(result3.get(1)));
        
        /**
         自訂用例: 兩陣列完全不同
         輸入: nums1 = [1, 2], nums2 = [3, 4]
         輸出: [[1,2],[3,4]]
         */
        List<List<Integer>> result4 = DifferenceOfTwoArrays.findDifference(new int[]{1, 2}, new int[]{3, 4});
        assertEquals(new HashSet<>(List.of(1, 2)), new HashSet<>(result4.get(0)));
        assertEquals(new HashSet<>(List.of(3, 4)), new HashSet<>(result4.get(1)));
    }
}
