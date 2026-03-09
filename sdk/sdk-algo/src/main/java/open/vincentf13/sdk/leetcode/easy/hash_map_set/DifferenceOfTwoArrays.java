package open.vincentf13.sdk.leetcode.easy.hash_map_set;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

/** 
 LeetCode 2215: Find the Difference of Two Arrays
 https://leetcode.com/problems/find-the-difference-of-two-arrays/
 
 Given two 0-indexed integer arrays nums1 and nums2, return a list answer of size 2 where:
 - answer[0] is a list of all distinct integers in nums1 which are not present in nums2.
 - answer[1] is a list of all distinct integers in nums2 which are not present in nums1.
 */
@UtilityClass
public class DifferenceOfTwoArrays {

    /** 
     找出兩個陣列中的獨有且不重複的整數列表。
     
     使用哈希集合 (Hash Set) 技巧：
     1. 將 nums1 和 nums2 的元素分別存入 Set，自動去重。
     2. 遍歷 nums1Set，若元素不在 nums2Set 中，加入結果 1。
     3. 遍歷 nums2Set，若元素不在 nums1Set 中，加入結果 2。
     
     時間複雜度: O(n + m)，n 和 m 分別為 nums1 和 nums2 的長度。
     空間複雜度: O(n + m)。
     
     @param nums1 第一個整數陣列
     @param nums2 第二個整數陣列
     @return 包含兩個差異列表的列表
     */
    public static List<List<Integer>> findDifference(int[] nums1, int[] nums2) {
        Set<Integer> set1 = new HashSet<>();
        for (int num : nums1) {
            set1.add(num);
        }
        
        Set<Integer> set2 = new HashSet<>();
        for (int num : nums2) {
            set2.add(num);
        }
        
        List<Integer> diff1 = new ArrayList<>();
        for (int num : set1) {
            if (!set2.contains(num)) {
                diff1.add(num);
            }
        }
        
        List<Integer> diff2 = new ArrayList<>();
        for (int num : set2) {
            if (!set1.contains(num)) {
                diff2.add(num);
            }
        }
        
        return List.of(diff1, diff2);
    }
}
