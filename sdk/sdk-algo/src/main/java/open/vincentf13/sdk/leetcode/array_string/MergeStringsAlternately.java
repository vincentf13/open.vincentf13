package open.vincentf13.sdk.leetcode.array_string;

import lombok.experimental.UtilityClass;

/**
  LeetCode 1768: Merge Strings Alternately https://leetcode.com/problems/merge-strings-alternately/

  You are given two strings word1 and word2. Merge the strings by adding letters in alternating
  order, starting with word1. If a string is longer than the other, append the additional letters
  onto the end of the merged string.
 */
@UtilityClass
public class MergeStringsAlternately {

  public static String mergeAlternately(String word1, String word2) {
    StringBuilder sb = new StringBuilder();
    if (word1 == null) word1 = "";
    if (word2 == null) word2 = "";
    if (word1.equals(""))return word2;
    if (word2.equals(""))return word1;

    int maxLength = Math.max(word1.length(), word2.length());
    for (int i = 0; i < maxLength; i++) {
      if (i < word1.length()) {
        sb.append(word1.charAt(i));
      }
      if (i < word2.length()) {
        sb.append(word2.charAt(i));
      }
    }
    return sb.toString();
  }
}
