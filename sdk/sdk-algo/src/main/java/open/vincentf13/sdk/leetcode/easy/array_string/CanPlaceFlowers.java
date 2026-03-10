package open.vincentf13.sdk.leetcode.easy.array_string;

import lombok.experimental.UtilityClass;

/**
 LeetCode 605: Can Place Flowers https://leetcode.com/problems/can-place-flowers/
 
 <p>給定一個花壇陣列 flowerbed，其中 0 表示空，1 表示已種花。 花不能種在相鄰的格子中。給定一個整數 n，判斷是否可以在不違反規則的情況下種入 n 朵新花。
 */
@UtilityClass
public class CanPlaceFlowers {
    
    /**
     判斷是否能在花壇中種入 n 朵新花
     
     @param flowerbed 花壇陣列
     @param n         要種入的花朵數量
     @return 是否可行
     */
    public static boolean canPlaceFlowers(int[] flowerbed,
                                          int n) {
        int count = 0;
        int length = flowerbed.length;
        
        for (int i = 0; i < length; i++) {
            // 只有當前位置是空的 (0) 才能考慮種花
            if (flowerbed[i] == 0) {
                // 檢查左側：如果是開頭或是左側為空
                boolean leftEmpty = (i == 0) || (flowerbed[i - 1] == 0);
                // 檢查右側：如果是末尾或是右側為空
                boolean rightEmpty = (i == length - 1) || (flowerbed[i + 1] == 0);
                
                if (leftEmpty && rightEmpty) {
                    // 種下一朵花
                    flowerbed[i] = 1;
                    count++;
                    
                    // 如果已經種夠了，提早結束
                    if (count >= n) {
                        return true;
                    }
                }
            }
        }
        
        return count >= n;
    }
}
