package open.vincentf13.sdk.leetcode.array_string;

import lombok.experimental.UtilityClass;
import open.vincentf13.sdk.leetcode.math.number.GreatestCommonDivisor;

/**
  LeetCode 1071: Greatest Common Divisor of Strings
  https://leetcode.com/problems/greatest-common-divisor-of-strings/

  For two strings s and t, we say "t divides s" if and only if s = t + t + t + ... + t + t
  (i.e., t is concatenated with itself one or more times).

  Given two strings str1 and str2, return the largest string x such that x divides both str1 and str2.
 */
@UtilityClass
public class GreatestCommonDivisorOfStrings {

    public static String gcdOfStrings(String str1, String str2) {
        // 若兩個字串正反拼接不相等，代表沒有共同的公因數字串
        if (!(str1 + str2).equals(str2 + str1)) {
            return "";
        }
        
        // 取得兩字串長度的最大公因數
        int gcdLength = GreatestCommonDivisor.gcd(str1.length(), str2.length());
        
        // 擷取最大公因數長度的子字串
        return str1.length() > str2.length() ?  str1.substring(0, gcdLength) : str2.substring(0, gcdLength);
    }
}