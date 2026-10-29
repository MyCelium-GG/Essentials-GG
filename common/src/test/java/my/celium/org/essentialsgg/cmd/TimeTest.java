package my.celium.org.essentialsgg.cmd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TimeTest {
    @Test
    void parsesUnitsAndCombinations() {
        assertEquals(30_000, Time.parse("30s"));
        assertEquals(600_000, Time.parse("10m"));
        assertEquals(600_000, Time.parse("10"));
        assertEquals(86_400_000L + 12 * 3_600_000L, Time.parse("1d12h"));
        assertEquals(2 * 604_800_000L, Time.parse("2w"));
        assertEquals(2_592_000_000L, Time.parse("1mo"));
        assertEquals(-1, Time.parse("soon"));
        assertEquals(-1, Time.parse("5x"));
        assertEquals(-1, Time.parse(""));
    }

    @Test
    void formatsLargestThreeUnits() {
        assertEquals("1d 5m", Time.format(86_400_000L + 300_000L, "d", "h", "m", "s"));
        assertEquals("2h 3m 4s", Time.format(2 * 3_600_000L + 3 * 60_000L + 4_000L, "d", "h", "m", "s"));
        assertEquals("1s", Time.format(10, "d", "h", "m", "s"));
    }
}
