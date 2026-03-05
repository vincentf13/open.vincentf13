package open.vincentf13.sdk.algo.greedy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CanPlaceFlowersTest {

    @Test
    @DisplayName("測試所有種花案例")
    void testCanPlaceFlowers() {
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0, 0, 1}, 1));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0, 0, 1}, 2));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 1, 0, 1}, 1));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 1, 0, 0}, 1));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0}, 0));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0}, 1));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{0}, 2));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1}, 0));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{1}, 1));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{1}, 2));
    }
}
