package my.celium.org.essentialsgg.cmd;

import java.util.Locale;

import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.server.level.ServerPlayer;

/** Durations: {@code 1d2h30m} in, {@code 1d 2h 30m} out (unit letters from the language file). */
public final class Time {
    private Time() {
    }

    /**
     * Parses {@code 30s}, {@code 10m}, {@code 2h}, {@code 7d}, {@code 2w},
     * {@code 1mo}, {@code 1y} and combinations like {@code 1d12h}. A bare
     * number counts as minutes. Returns millis, or -1 when it is not a duration.
     */
    public static long parse(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return -1;
        }
        if (s.chars().allMatch(Character::isDigit)) {
            return safeTimes(Long.parseLong(s), 60_000L);
        }
        long total = 0;
        int i = 0;
        while (i < s.length()) {
            int start = i;
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (start == i) {
                return -1;
            }
            long amount = Long.parseLong(s.substring(start, Math.min(i, start + 12)));
            int unitStart = i;
            while (i < s.length() && Character.isLetter(s.charAt(i))) {
                i++;
            }
            long unit = switch (s.substring(unitStart, i)) {
                case "s", "sec", "secs", "second", "seconds" -> 1_000L;
                case "m", "min", "mins", "minute", "minutes" -> 60_000L;
                case "h", "hr", "hrs", "hour", "hours" -> 3_600_000L;
                case "d", "day", "days" -> 86_400_000L;
                case "w", "week", "weeks" -> 604_800_000L;
                case "mo", "month", "months" -> 2_592_000_000L;
                case "y", "year", "years" -> 31_536_000_000L;
                default -> -1L;
            };
            if (unit < 0) {
                return -1;
            }
            total = safeAdd(total, safeTimes(amount, unit));
        }
        return total;
    }

    /** {@code 1d 2h 5m}, at most the three largest units; under a second shows {@code 1s}. */
    public static String format(ServerPlayer player, long millis) {
        String lang = Lang.languageOf(player);
        return format(millis, Lang.raw(lang, "time.d"), Lang.raw(lang, "time.h"), Lang.raw(lang, "time.m"),
                Lang.raw(lang, "time.s"));
    }

    static String format(long millis, String d, String h, String m, String s) {
        long seconds = Math.max(1, (millis + 999) / 1000);
        long[] values = {seconds / 86_400, seconds / 3_600 % 24, seconds / 60 % 60, seconds % 60};
        String[] units = {d, h, m, s};
        StringBuilder out = new StringBuilder();
        int shown = 0;
        // Zero units are left out: 1d 5m, not 1d 0h 5m.
        for (int i = 0; i < values.length && shown < 3; i++) {
            if (values[i] > 0) {
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(values[i]).append(units[i]);
                shown++;
            }
        }
        return out.toString();
    }

    private static long safeTimes(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE / 2;
        }
    }

    private static long safeAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE / 2;
        }
    }
}
