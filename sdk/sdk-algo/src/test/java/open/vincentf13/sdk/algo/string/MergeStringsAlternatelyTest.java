package open.vincentf13.sdk.algo.string;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
  MergeStringsAlternately 演算法單元測試
 */
class MergeStringsAlternatelyTest {

    @Test
    @DisplayName("測試所有交替合併字串案例")
    void testMergeAlternately() {
        // 案例 1: 等長字串 "abc", "pqr" -> "apbqcr"
        assertEquals("apbqcr", MergeStringsAlternately.mergeAlternately("abc", "pqr"));
        
        // 案例 2: word2 較長 "ab", "pqrs" -> "apbqrs"
        assertEquals("apbqrs", MergeStringsAlternately.mergeAlternately("ab", "pqrs"));
        
        // 案例 3: word1 較長 "abcd", "pq" -> "apbqcd"
        assertEquals("apbqcd", MergeStringsAlternately.mergeAlternately("abcd", "pq"));
    }
}
