package my.celium.org.essentialsgg.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class MiniYamlTest {
    /** Shaped like an EssentialsX userdata file. */
    private static final String USER = """
            # comment
            last-account-name: Steve
            money: '1234.5'
            nickname: '&cSte''ve'
            homes:
              base:
                world: 6f7c0c4e-0000-0000-0000-000000000000
                world-name: world_nether
                x: 10.5
                y: 64.0
                z: -3.25
                yaw: 90.0
                pitch: 0.0
              empty: {}
            timestamps:
              logout: 1700000000000
            ignore:
            - 1b2c
            muted: false
            """;

    @Test
    void readsNestedMapsAndQuotes() {
        Map<String, Object> yaml = MiniYaml.parse(USER);
        assertEquals("Steve", MiniYaml.string(yaml, "last-account-name"));
        assertEquals(1234.5, MiniYaml.number(yaml, "money", 0));
        assertEquals("&cSte've", MiniYaml.string(yaml, "nickname"));
        Map<String, Object> base = MiniYaml.map(MiniYaml.map(yaml, "homes"), "base");
        assertEquals("world_nether", MiniYaml.string(base, "world-name"));
        assertEquals(-3.25, MiniYaml.number(base, "z", 0));
        assertEquals(1700000000000.0, MiniYaml.number(MiniYaml.map(yaml, "timestamps"), "logout", 0));
        assertEquals("false", MiniYaml.string(yaml, "muted"));
        assertTrue(MiniYaml.map(yaml, "ignore").isEmpty());
    }
}
