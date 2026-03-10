package open.vincentf13.sdk.leetcode.easy.two_pointers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsSubsequenceTest {
    
    @Test
    @DisplayName("測試判斷子序列案例")
    void testIsSubsequence() {
        /**
         LeetCode 官方用例 1
         輸入: s = "abc", t = "ahbgdc"
         輸出: true
         */
        assertTrue(IsSubsequence.isSubsequence("abc", "ahbgdc"));
        
        /**
         LeetCode 官方用例 2
         輸入: s = "axc", t = "ahbgdc"
         輸出: false
         */
        assertFalse(IsSubsequence.isSubsequence("axc", "ahbgdc"));
        
        /**
         自訂用例: 空子序列
         s 是空的，對任何 t 都是子序列
         */
        assertTrue(IsSubsequence.isSubsequence("", "ahbgdc"));
        
        /**
         自訂用例: t 是空的
         s 不為空，則不是子序列
         */
        assertFalse(IsSubsequence.isSubsequence("abc", ""));
        
        /**
         自訂用例: 兩者皆為空
         */
        assertTrue(IsSubsequence.isSubsequence("", ""));
        
        /**
         自訂用例: 字元順序錯誤
         */
        assertFalse(IsSubsequence.isSubsequence("acb", "ahbgdc"));
    }
}
