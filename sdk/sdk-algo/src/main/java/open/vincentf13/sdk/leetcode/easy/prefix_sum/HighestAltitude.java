package open.vincentf13.sdk.leetcode.easy.prefix_sum;

import lombok.experimental.UtilityClass;

/**
 LeetCode 1732: Find the Highest Altitude
 https://leetcode.com/problems/find-the-highest-altitude/
 
 There is a biker going on a road trip. The road trip consists of n + 1 points at different altitudes.
 The biker starts his trip on point 0 with altitude 0.
 You are given an integer array gain of length n where gain[i] is the net gain in altitude between points i and i + 1.
 Return the highest altitude of a point.
 */
@UtilityClass
public class HighestAltitude {
    
    /**
     計算這趟旅行中所有點的最高海拔。
     
     使用前綴和 (Prefix Sum) 概念：
     1. 自行車手從海拔 0 開始。
     2. 遍歷 gain 陣列，逐步加上海拔增益。
     3. 在每個步驟中，更新當前的最大海拔值。
     
     時間複雜度: O(n)，n 為 gain 陣列的長度。
     空間複雜度: O(1)，僅使用常數額外空間存儲當前海拔與最大海拔。
     
     @param gain 海拔淨增益陣列
     @return 最高海拔值
     */
    public static int largestAltitude(int[] gain) {
        int currentAltitude = 0;
        int maxAltitude = 0;
        
        for (int g : gain) {
            currentAltitude += g;
            if (currentAltitude > maxAltitude) {
                maxAltitude = currentAltitude;
            }
        }
        
        return maxAltitude;
    }
}
