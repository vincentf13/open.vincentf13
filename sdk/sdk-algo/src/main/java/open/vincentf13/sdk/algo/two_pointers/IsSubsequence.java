package open.vincentf13.sdk.algo.two_pointers;

import lombok.experimental.UtilityClass;

/**
 * LeetCode 392: Is Subsequence https://leetcode.com/problems/is-subsequence/
 *
 * <p>Given two strings s and t, return true if s is a subsequence of t, or false otherwise. A
 * subsequence of a string is a new string that is formed from the original string by deleting some
 * (can be none) of the characters without disturbing the relative positions of the remaining
 * characters.
 */
@UtilityClass
public class IsSubsequence {

  /**
   * 判斷 s 是否為 t 的子序列。
   *
   * <p>使用雙指標法： - 指標 i 指向 s，指標 j 指向 t。 - 當 s[i] == t[j] 時，代表找到一個匹配字元，i 前進。 - 無論是否匹配，j 始終前進。 - 若最後 i
   * 等於 s 的長度，說明 s 的所有字元都在 t 中按順序找到了。
   *
   * <p>時間複雜度: O(n)，n 為字串 t 的長度。 空間複雜度: O(1)。
   *
   * @param s 子序列候選字串
   * @param t 原始字串
   * @return 若 s 是 t 的子序列則返回 true
   */
  public static boolean isSubsequence(String s, String t) {
    if (s == null || t == null) return false;
    if (s.isEmpty()) return true;

    int i = 0;
    int j = 0;
    while (i < s.length() && j < t.length()) {
      if(s.charAt(i) == t.charAt(j)) {
        i++;
      }
      j++;
    }
    
    return i==s.length();
  }
}
