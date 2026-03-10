package open.vincentf13.sdk.leetcode.easy.prefix_sum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HighestAltitudeTest {
    
    @Test
    @DisplayName("測試最高海拔案例")
    void testLargestAltitude() {
        /**
         LeetCode 官方用例 1
         輸入: gain = [-5,1,5,0,-7]
         海拔變化: [0, -5, -4, 1, 1, -6]
         輸出: 1
         */
        assertEquals(1, HighestAltitude.largestAltitude(new int[]{-5, 1, 5, 0, -7}));
        
        /**
         LeetCode 官方用例 2
         輸入: gain = [-4,-3,-2,-1,4,3,2]
         海拔變化: [0, -4, -7, -9, -10, -6, -3, -1]
         輸出: 0
         */
        assertEquals(0, HighestAltitude.largestAltitude(new int[]{-4, -3, -2, -1, 4, 3, 2}));
        
        /**
         自訂用例: 全部為正增益
         輸入: gain = [1, 2, 3]
         海拔變化: [0, 1, 3, 6]
         輸出: 6
         */
        assertEquals(6, HighestAltitude.largestAltitude(new int[]{1, 2, 3}));
        
        /**
         自訂用例: 單個增益
         輸入: gain = [-10]
         海拔變化: [0, -10]
         輸出: 0
         */
        assertEquals(0, HighestAltitude.largestAltitude(new int[]{-10}));
    }
}
