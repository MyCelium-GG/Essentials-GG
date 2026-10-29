package my.celium.org.essentialsgg.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalPermsTest {
    @Test
    void exactBeatsWildcardsAndDeeperWildcardsWin() {
        String node = "essentialsgg.kit.vip";
        assertEquals(Integer.MAX_VALUE, LocalPerms.specificity(node, node));
        assertTrue(LocalPerms.specificity("essentialsgg.kit.*", node) > LocalPerms.specificity("essentialsgg.*", node));
        assertTrue(LocalPerms.specificity("essentialsgg.*", node) > LocalPerms.specificity("*", node));
        assertEquals(0, LocalPerms.specificity("*", node));
    }

    @Test
    void wildcardsOnlyMatchWholeSegments() {
        assertEquals(-1, LocalPerms.specificity("essentialsgg.kit.*", "essentialsgg.kits"));
        assertEquals(-1, LocalPerms.specificity("essentialsgg.home", "essentialsgg.homes.others"));
        assertEquals(-1, LocalPerms.specificity("other.*", "essentialsgg.home"));
    }
}
