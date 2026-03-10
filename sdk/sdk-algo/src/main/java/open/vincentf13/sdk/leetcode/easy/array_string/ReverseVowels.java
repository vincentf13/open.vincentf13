package open.vincentf13.sdk.leetcode.easy.array_string;

import lombok.experimental.UtilityClass;

import java.util.Set;

/**
 LeetCode 345: Reverse Vowels of a String
 https://leetcode.com/problems/reverse-vowels-of-a-string/
 
 <p>給定一個字串 s，僅反轉字串中的所有元音字母，並返回結果。 元音字母包括 'a', 'e', 'i', 'o', 'u'，且包含大小寫。
 */
@UtilityClass
public class ReverseVowels {
    
    private static final Set<Character> VOWELS =
            Set.of('a', 'e', 'i', 'o', 'u', 'A', 'E', 'I', 'O', 'U');
    
    /**
     反轉字串中的元音字母
     
     @param s 輸入字串
     @return 反轉元音後的字串
     */
    public static String reverseVowels(String s) {
        if (s == null || s.isEmpty())
            return s;
        
        char[] chars = s.toCharArray();
        int left = 0;
        int right = s.length() - 1;
        
        while (left < right) {
            
            while (left < right && !VOWELS.contains(chars[left])) {
                left++;
            }
            while (left < right && !VOWELS.contains(chars[right])) {
                right--;
            }
            
            if (left < right) {
                char temp = chars[left];
                chars[left] = chars[right];
                chars[right] = temp;
                left++;
                right--;
            }
        }
        
        return new String(chars);
    }
}
