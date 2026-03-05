package open.vincentf13.sdk.algo.string;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
  GreatestCommonDivisorOfStrings 演算法單元測試
 */
class GreatestCommonDivisorOfStringsTest {

    @Test
    @DisplayName("測試所有字串最大公因數案例")
    void testGcdOfStrings() {
        // 案例 1: "ABCABC", "ABC" -> "ABC"
        assertEquals("ABC", GreatestCommonDivisorOfStrings.gcdOfStrings("ABCABC", "ABC"));
        
        // 案例 2: "ABABAB", "ABAB" -> "AB"
        assertEquals("AB", GreatestCommonDivisorOfStrings.gcdOfStrings("ABABAB", "ABAB"));
        
        // 案例 3: "LEET", "CODE" -> ""
        assertEquals("", GreatestCommonDivisorOfStrings.gcdOfStrings("LEET", "CODE"));
    }
}
