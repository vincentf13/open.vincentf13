package open.vincentf13.sdk.algo.greedy;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CanPlaceFlowersTest {

    @Test
    void testCanPlaceFlowers() {
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{}, 0));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{}, 1));

        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0}, 1));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{0}, 2));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{1}, 1));

        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0}, 1));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0}, 2));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0}, 1));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 1}, 1));

        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 0}, 2));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 0}, 3));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0}, 1));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 1}, 1));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 1, 0}, 1));

        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 0, 0}, 2));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 0, 0}, 3));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 1, 0}, 1));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 1, 0, 0}, 1));

        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 0, 0, 0}, 3));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 0, 0, 0}, 4));
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0, 0, 1}, 1));
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0, 0, 1}, 2));

        for (int i = 6; i <= 10; i++) {
            assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[i], (i + 1) / 2));
            assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[i], (i + 1) / 2 + 1));
            
            int[] full = new int[i];
            Arrays.fill(full, 1);
            assertTrue(CanPlaceFlowers.canPlaceFlowers(full, 0));
            assertFalse(CanPlaceFlowers.canPlaceFlowers(full, 1));
        }
    }
}
