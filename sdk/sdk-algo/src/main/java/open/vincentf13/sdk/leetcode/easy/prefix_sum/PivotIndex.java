package open.vincentf13.sdk.leetcode.easy.prefix_sum;

import lombok.experimental.UtilityClass;

/** 
 LeetCode 724: Find Pivot Index
 https://leetcode.com/problems/find-pivot-index/
 
 The pivot index is the index where the sum of all the numbers strictly to the left of the index is equal to 
 the sum of all the numbers strictly to the index's right.
 If the index is on the left edge of the array, then the left sum is 0 because there are no elements to the left. 
 This also applies to the right edge of the array.
 Return the leftmost pivot index. If no such index exists, return -1.
 */
@UtilityClass
public class PivotIndex {

    /** 
     尋找陣列的樞紐索引。
     
     使用前綴和概念：
     1. 先計算整個陣列的總和 totalSum。
     2. 遍歷陣列，維護當前位置左側的總和 leftSum。
     3. 對於每個位置 i，右側的總和 rightSum = totalSum - leftSum - nums[i]。
     4. 若 leftSum == rightSum，則 i 即為樞紐索引。
     
     時間複雜度: O(n)，遍歷兩次陣列。
     空間複雜度: O(1)，僅使用常數額外空間。
     
     @param nums 整數陣列
     @return 最左側的樞紐索引，若不存在則返回 -1
     */
    public static int pivotIndex(int[] nums) {
        int totalSum = 0;
        for (int num : nums) {
            totalSum += num;
        }
        
        int leftSum = 0;
        for (int i = 0; i < nums.length; i++) {
            // 左側總和等於右側總和的條件：leftSum == totalSum - leftSum - nums[i]
            // 也可以寫成：2 * leftSum == totalSum - nums[i]
            if (leftSum * 2 == totalSum - nums[i]) {
                return i;
                
            }
            leftSum += nums[i];
        }
        
        return -1;
    }
}
