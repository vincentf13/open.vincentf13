package open.vincentf13.sdk.leetcode.easy.array_string;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KidsWithCandiesTest {
    
    @Test
    @DisplayName("測試所有小孩糖果案例")
    void testKidsWithCandies() {
        assertEquals(List.of(true, true, true, false, true),
                     KidsWithCandies.kidsWithCandies(new int[]{2, 3, 5, 1, 3}, 3));
        assertEquals(List.of(true, false, false, false, false),
                     KidsWithCandies.kidsWithCandies(new int[]{4, 2, 1, 1, 2}, 1));
        assertEquals(List.of(true, false, true),
                     KidsWithCandies.kidsWithCandies(new int[]{12, 1, 12}, 10));
    }
}
