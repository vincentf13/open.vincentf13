package open.vincentf13.sdk.algo.string;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GreatestCommonDivisorOfStringsTest {

    @Test
    void testExample1() {
        // Input: str1 = "ABCABC", str2 = "ABC"
        // Output: "ABC"
        String str1 = "ABCABC";
        String str2 = "ABC";
        String expected = "ABC";

        assertEquals(expected, GreatestCommonDivisorOfStrings.gcdOfStrings(str1, str2));
    }

    @Test
    void testExample2() {
        // Input: str1 = "ABABAB", str2 = "ABAB"
        // Output: "AB"
        String str1 = "ABABAB";
        String str2 = "ABAB";
        String expected = "AB";

        assertEquals(expected, GreatestCommonDivisorOfStrings.gcdOfStrings(str1, str2));
    }

    @Test
    void testExample3() {
        // Input: str1 = "LEET", str2 = "CODE"
        // Output: ""
        String str1 = "LEET";
        String str2 = "CODE";
        String expected = "";

        assertEquals(expected, GreatestCommonDivisorOfStrings.gcdOfStrings(str1, str2));
    }
}
