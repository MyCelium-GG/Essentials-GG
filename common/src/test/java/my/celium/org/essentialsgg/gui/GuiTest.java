package my.celium.org.essentialsgg.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class GuiTest {
    @Test
    void rowsAreCenteredAndSymmetrical() {
        assertArrayEquals(new int[] {}, Gui.centered(0));
        assertArrayEquals(new int[] {4}, Gui.centered(1));
        assertArrayEquals(new int[] {3, 5}, Gui.centered(2));
        assertArrayEquals(new int[] {3, 4, 5}, Gui.centered(3));
        assertArrayEquals(new int[] {2, 3, 5, 6}, Gui.centered(4));
        assertArrayEquals(new int[] {2, 3, 4, 5, 6}, Gui.centered(5));
        assertArrayEquals(new int[] {1, 2, 3, 5, 6, 7}, Gui.centered(6));
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6, 7}, Gui.centered(7));
    }
}
