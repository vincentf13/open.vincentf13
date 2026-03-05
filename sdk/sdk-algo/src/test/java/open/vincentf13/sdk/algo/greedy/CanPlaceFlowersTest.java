package open.vincentf13.sdk.algo.greedy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CanPlaceFlowersTest {

    @Test
    void testCanPlaceFlowers() {
        for (int i = 1; i <= 10; i++) {
            assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[i], (i + 1) / 2));
            assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[i], (i + 1) / 2 + 1));
        }

        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0, 0, 1}, 1));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0, 0, 1}, 2));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 1, 0, 1}, 1));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 1, 0, 0}, 1));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 1, 0}, 1));
    }
}
