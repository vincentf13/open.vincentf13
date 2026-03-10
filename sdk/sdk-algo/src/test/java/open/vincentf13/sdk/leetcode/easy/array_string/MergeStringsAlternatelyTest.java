package open.vincentf13.sdk.leetcode.easy.array_string;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MergeStringsAlternatelyTest {
    
    @Test
    @DisplayName("測試所有交替合併字串案例")
    void testMergeAlternately() {
        assertEquals("", MergeStringsAlternately.mergeAlternately("", ""));
        assertEquals("a", MergeStringsAlternately.mergeAlternately("a", ""));
        assertEquals("b", MergeStringsAlternately.mergeAlternately("", "b"));
        assertEquals("ab", MergeStringsAlternately.mergeAlternately("a", "b"));
        assertEquals("apbqcr", MergeStringsAlternately.mergeAlternately("abc", "pqr"));
        assertEquals("apbqrs", MergeStringsAlternately.mergeAlternately("ab", "pqrs"));
        assertEquals("apbqcd", MergeStringsAlternately.mergeAlternately("abcd", "pq"));
    }
}
