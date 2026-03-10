package open.vincentf13.sdk.leetcode.easy.array_string;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReverseVowelsTest {
    
    @Test
    void testReverseVowels() {
        assertNull(ReverseVowels.reverseVowels(null));
        assertEquals("", ReverseVowels.reverseVowels(""));
        
        assertEquals("a", ReverseVowels.reverseVowels("a"));
        assertEquals("b", ReverseVowels.reverseVowels("b"));
        
        assertEquals("ab", ReverseVowels.reverseVowels("ab"));
        assertEquals("ba", ReverseVowels.reverseVowels("ba"));
        assertEquals("oa", ReverseVowels.reverseVowels("ao"));
        assertEquals("12", ReverseVowels.reverseVowels("12"));
        
        assertEquals("abc", ReverseVowels.reverseVowels("abc"));
        assertEquals("ob a", ReverseVowels.reverseVowels("ab o"));
        assertEquals("uoa", ReverseVowels.reverseVowels("aou"));
        assertEquals("e1a", ReverseVowels.reverseVowels("a1e"));
        
        assertEquals("ouae", ReverseVowels.reverseVowels("eauo"));
        assertEquals("u aoe", ReverseVowels.reverseVowels("e oau"));
        assertEquals("1a2e3", ReverseVowels.reverseVowels("1e2a3"));
        
        assertEquals("leotcede", ReverseVowels.reverseVowels("leetcode"));
        assertEquals("holle", ReverseVowels.reverseVowels("hello"));
        assertEquals("Aa", ReverseVowels.reverseVowels("aA"));
    }
}
