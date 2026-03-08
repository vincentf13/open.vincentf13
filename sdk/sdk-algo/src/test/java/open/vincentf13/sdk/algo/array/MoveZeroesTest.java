package open.vincentf13.sdk.algo.array;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MoveZeroesTest {

    @Test
    @DisplayName("測試移動零案例")
    void testMoveZeroes() {
        /** 
         LeetCode 官方用例 1
         輸入: nums = [0,1,0,3,12]
         輸出: [1,3,12,0,0]
         */
        int[] nums1 = {0, 1, 0, 3, 12};
        MoveZeroes.moveZeroes(nums1);
        assertArrayEquals(new int[]{1, 3, 12, 0, 0}, nums1);

        /** 
         LeetCode 官方用例 2
         輸入: nums = [0]
         輸出: [0]
         */
        int[] nums2 = {0};
        MoveZeroes.moveZeroes(nums2);
        assertArrayEquals(new int[]{0}, nums2);

        /** 
         自訂用例: 已經排序且無 0
         輸入: nums = [1, 2, 3]
         輸出: [1, 2, 3]
         */
        int[] nums3 = {1, 2, 3};
        MoveZeroes.moveZeroes(nums3);
        assertArrayEquals(new int[]{1, 2, 3}, nums3);

        /** 
         自訂用例: 全部為 0
         輸入: nums = [0, 0, 0]
         輸出: [0, 0, 0]
         */
        int[] nums4 = {0, 0, 0};
        MoveZeroes.moveZeroes(nums4);
        assertArrayEquals(new int[]{0, 0, 0}, nums4);

        /** 
         自訂用例: 0 在末尾
         輸入: nums = [1, 0]
         輸出: [1, 0]
         */
        int[] nums5 = {1, 0};
        MoveZeroes.moveZeroes(nums5);
        assertArrayEquals(new int[]{1, 0}, nums5);
    }
}
