package open.vincentf13.sdk.algo.string;

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
    }
}
