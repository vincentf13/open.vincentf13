package open.vincentf13.sdk.algo.sliding_window;

import lombok.experimental.UtilityClass;

/** 
 LeetCode 643: Maximum Average Subarray I
 https://leetcode.com/problems/maximum-average-subarray-i/
 
 Find a contiguous subarray whose length is equal to k that has the maximum average value and return this value.
 Any answer with a calculation error less than 10^-5 will be accepted.
 */
@UtilityClass
public class MaxAverageSubarrayI {

    /** 
     找出長度為 k 的連續子陣列的最大平均值。
     
     使用滑動視窗 (Sliding Window) 技巧：
     1. 先計算前 k 個元素的總和。
     2. 視窗向右滑動，每次減去最左邊的元素並加上新進入最右邊的元素。
     3. 記錄滑動過程中的最大總和。
     4. 最後將最大總和除以 k 得到最大平均值。
     
     時間複雜度: O(n)，n 為 nums 的長度。
     空間複雜度: O(1)。
     
     @param nums 整數陣列
     @param k 子陣列長度
     @return 最大平均值
     */
    public static double findMaxAverage(int[] nums, int k) {
        // 先計算第一個視窗的總和
        long sum = 0;
        for (int i = 0; i < k; i++) {
            sum += nums[i];
        }
        
        long maxSum = sum;
        
        // 開始滑動視窗
        for (int i = k; i < nums.length; i++) {
            sum = sum - nums[i - k] + nums[i];
            if (sum > maxSum) {
                maxSum = sum;
            }
        }
        
        return (double) maxSum / k;
    }
}
