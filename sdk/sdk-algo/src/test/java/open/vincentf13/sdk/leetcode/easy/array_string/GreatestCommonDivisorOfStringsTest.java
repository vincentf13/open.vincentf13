package open.vincentf13.sdk.leetcode.easy.array_string;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreatestCommonDivisorOfStringsTest {
    
    @Test
    @DisplayName("測試所有字串最大公因數案例")
    void testGcdOfStrings() {
        assertEquals("ABC", GreatestCommonDivisorOfStrings.gcdOfStrings("ABCABC", "ABC"));
        assertEquals("AB", GreatestCommonDivisorOfStrings.gcdOfStrings("ABABAB", "ABAB"));
        assertEquals("", GreatestCommonDivisorOfStrings.gcdOfStrings("LEET", "CODE"));
        
        assertEquals("", GreatestCommonDivisorOfStrings.gcdOfStrings("", ""));
        assertEquals("AB", GreatestCommonDivisorOfStrings.gcdOfStrings("AB", ""));
        assertEquals("AB", GreatestCommonDivisorOfStrings.gcdOfStrings("", "AB"));
        assertEquals("AB", GreatestCommonDivisorOfStrings.gcdOfStrings("AB", "AB"));
        assertEquals("AB", GreatestCommonDivisorOfStrings.gcdOfStrings("ABAB", "AB"));
        assertEquals("AB", GreatestCommonDivisorOfStrings.gcdOfStrings("AB", "ABAB"));
        assertEquals("ABAB", GreatestCommonDivisorOfStrings.gcdOfStrings("ABAB", "ABAB"));
    }
}
