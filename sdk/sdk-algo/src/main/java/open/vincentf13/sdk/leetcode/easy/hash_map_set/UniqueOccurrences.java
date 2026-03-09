package open.vincentf13.sdk.leetcode.easy.hash_map_set;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

/** 
 LeetCode 1207: Unique Number of Occurrences
 https://leetcode.com/problems/unique-number-of-occurrences/
 
 Given an array of integers arr, return true if the number of occurrences of each value in the array is unique, 
 or false otherwise.
 */
@UtilityClass
public class UniqueOccurrences {

    /** 
     判斷陣列中每個數值出現的次數是否都是獨一無二的。
     
     使用哈希表與哈希集合：
     1. 使用 HashMap 統計每個數字出現的次數。
     2. 使用 HashSet 儲存所有的出現次數。
     3. 比較 HashMap 的值的數量與 HashSet 的大小，若相等則代表所有次數都是唯一的。
     
     時間複雜度: O(n)，n 為陣列長度。
     空間複雜度: O(n)。
     
     @param arr 整數陣列
     @return 若所有出現次數皆唯一則返回 true
     */
    public static boolean uniqueOccurrences(int[] arr) {
        Map<Integer, Integer> countMap = new HashMap<>();
        for (int num : arr) {
            countMap.put(num, countMap.getOrDefault(num, 0) + 1);
        }
        
        Set<Integer> occurrenceSet = new HashSet<>(countMap.values());
        
        return countMap.size() == occurrenceSet.size();
    }
}
