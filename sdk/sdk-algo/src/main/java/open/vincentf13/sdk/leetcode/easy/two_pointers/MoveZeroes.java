package open.vincentf13.sdk.leetcode.easy.two_pointers;

import lombok.experimental.UtilityClass;

/**
 * LeetCode 283: Move Zeroes https://leetcode.com/problems/move-zeroes/
 *
 * <p>Given an integer array nums, move all 0's to the end of it while maintaining the relative
 * order of the non-zero elements. Note that you must do this in-place without making a copy of the
 * array.
 */
@UtilityClass
public class MoveZeroes {

  /**
   * 將陣列中的所有 0 移動到末尾，同時保持非零元素的相對順序。
   *
   * <p>使用雙指標法： - 指標 i 用於遍歷陣列。 - 指標 lastNonZeroIndex 用於記錄下一個非零元素應該放置的位置。 當遇到非零元素時，將其與
   * lastNonZeroIndex 所在的元素交換，並遞增 lastNonZeroIndex。 這樣可以確保 lastNonZeroIndex 之前的元素都是非零的，且順序保持不變。
   *
   * @param nums 整數陣列
   */
  public static void moveZeroes(int[] nums) {
    int lastNonZeroIndex = 0;
    for (int i = 0; i < nums.length; i++) {
      if (nums[i] != 0) {

        int temp = nums[i];
        nums[i] = nums[lastNonZeroIndex];
        nums[lastNonZeroIndex] = temp;

        lastNonZeroIndex++;
      }
    }
  }
}
