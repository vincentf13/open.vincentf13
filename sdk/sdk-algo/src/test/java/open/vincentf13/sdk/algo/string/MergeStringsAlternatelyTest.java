package open.vincentf13.sdk.algo.string;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MergeStringsAlternatelyTest {

    @Test
    void testEqualLength() {
        // Example 1:
        // Input: word1 = "abc", word2 = "pqr"
        // Output: "apbqcr"
        String word1 = "abc";
        String word2 = "pqr";
        String expected = "apbqcr";

        assertEquals(expected, MergeStringsAlternately.mergeAlternately(word1, word2));
    }

    @Test
    void testWord2Longer() {
        // Example 2:
        // Input: word1 = "ab", word2 = "pqrs"
        // Output: "apbqrs"
        String word1 = "ab";
        String word2 = "pqrs";
        String expected = "apbqrs";

        assertEquals(expected, MergeStringsAlternately.mergeAlternately(word1, word2));
    }

    @Test
    void testWord1Longer() {
        // Example 3:
        // Input: word1 = "abcd", word2 = "pq"
        // Output: "apbqcd"
        String word1 = "abcd";
        String word2 = "pq";
        String expected = "apbqcd";

        assertEquals(expected, MergeStringsAlternately.mergeAlternately(word1, word2));
    }
}
