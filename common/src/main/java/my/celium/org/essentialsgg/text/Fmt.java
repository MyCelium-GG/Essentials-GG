package my.celium.org.essentialsgg.text;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Turns formatted text into a component. Messages, chat, nicknames, the MOTD
 * and the sidebar all go through here, and both popular formats work, mixed
 * freely:
 *
 * <ul>
 *   <li><b>Legacy codes</b> with {@code &} (or {@code §}): {@code &0}-{@code &f},
 *       {@code &l &o &n &m &k}, {@code &r}; hex as {@code &#ff55aa},
 *       {@code {#ff55aa}} or {@code &x&f&f&5&5&a&a}. {@code &&} is a literal
 *       {@code &}.</li>
 *   <li><b>MiniMessage tags</b>: {@code <red>}, {@code <#ff55aa>},
 *       {@code <color:gold>}, {@code <bold>}/{@code <b>}, {@code <italic>}/{@code <i>},
 *       {@code <underlined>}/{@code <u>}, {@code <strikethrough>}/{@code <st>},
 *       {@code <obfuscated>}/{@code <obf>}, {@code <!bold>}, {@code <reset>},
 *       {@code <gradient:red:#ff55aa>}, {@code <rainbow>}, {@code <newline>}/{@code <br>},
 *       {@code <hover:show_text:'text'>}, {@code <click:run_command:'/spawn'>}
 *       ({@code suggest_command}, {@code open_url}, {@code copy_to_clipboard}).
 *       Closing tags are optional; {@code \<} is a literal {@code <}.</li>
 *   <li><b>Placeholders</b> {@code {name}} from a map are inserted as
 *       components and never parsed, so player input in them stays as typed.</li>
 * </ul>
 *
 * <p>{@link Allow} limits what is honoured, so chat can give players colors
 * without click events. Anything not allowed or unknown stays as typed.
 * Pure logic, no registries: unit-tested.
 */
public final class Fmt {
    private static final Map<String, Integer> NAMED = new HashMap<>();
    private static final Map<Character, String> LEGACY_COLORS = new HashMap<>();
    private static final Map<String, Deco> DECO_TAGS = new HashMap<>();

    static {
        String[][] colors = {
            {"0", "black", "000000"}, {"1", "dark_blue", "0000AA"}, {"2", "dark_green", "00AA00"},
            {"3", "dark_aqua", "00AAAA"}, {"4", "dark_red", "AA0000"}, {"5", "dark_purple", "AA00AA"},
            {"6", "gold", "FFAA00"}, {"7", "gray", "AAAAAA"}, {"8", "dark_gray", "555555"},
            {"9", "blue", "5555FF"}, {"a", "green", "55FF55"}, {"b", "aqua", "55FFFF"},
            {"c", "red", "FF5555"}, {"d", "light_purple", "FF55FF"}, {"e", "yellow", "FFFF55"},
            {"f", "white", "FFFFFF"},
        };
        for (String[] c : colors) {
            NAMED.put(c[1], Integer.parseInt(c[2], 16));
            LEGACY_COLORS.put(c[0].charAt(0), c[1]);
        }
        NAMED.put("grey", NAMED.get("gray"));
        NAMED.put("dark_grey", NAMED.get("dark_gray"));
        for (Deco deco : Deco.values()) {
            for (String alias : deco.aliases) {
                DECO_TAGS.put(alias, deco);
            }
        }
    }

    /** What a piece of text may use. Messages from the server get {@link #ALL}; players get what their permissions say. */
    public record Allow(boolean colors, boolean hex, boolean gradients, boolean decorations, boolean events) {
        public static final Allow ALL = new Allow(true, true, true, true, true);
        public static final Allow NONE = new Allow(false, false, false, false, false);
        /** Everything but click and hover events: nicknames, player chat with full rights. */
        public static final Allow STYLE = new Allow(true, true, true, true, false);
    }

    enum Deco {
        BOLD('l', "bold", "b"),
        ITALIC('o', "italic", "i", "em"),
        UNDERLINED('n', "underlined", "underline", "u"),
        STRIKETHROUGH('m', "strikethrough", "st"),
        OBFUSCATED('k', "obfuscated", "obf");

        final char legacy;
        final String[] aliases;

        Deco(char legacy, String... aliases) {
            this.legacy = legacy;
            this.aliases = aliases;
        }

        Style apply(Style style, boolean on) {
            return switch (this) {
                case BOLD -> style.withBold(on);
                case ITALIC -> style.withItalic(on);
                case UNDERLINED -> style.withUnderlined(on);
                case STRIKETHROUGH -> style.withStrikethrough(on);
                case OBFUSCATED -> style.withObfuscated(on);
            };
        }

        static Deco ofLegacy(char code) {
            for (Deco deco : values()) {
                if (deco.legacy == code) {
                    return deco;
                }
            }
            return null;
        }
    }

    /** One open tag or legacy code. Exactly one of its effects is set. */
    private static final class Frame {
        final String tag;
        final boolean legacy;
        Integer color;
        Deco deco;
        boolean decoOn;
        int[] gradient;
        boolean rainbow;
        boolean reversed;
        HoverEvent hover;
        ClickEvent click;
        /** Characters a gradient or rainbow spans, and how many were colored so far. */
        int total;
        int seen;

        Frame(String tag, boolean legacy) {
            this.tag = tag;
            this.legacy = legacy;
        }
    }

    /** A character (codepoint) or an inserted placeholder component, with the frames open at that point. */
    private record Token(int codePoint, Component insert, Frame[] frames) {
    }

    private Fmt() {
    }

    /** Server-authored text, everything allowed. */
    public static MutableComponent parse(String raw) {
        return parse(raw, Allow.ALL, Map.of());
    }

    /** Server-authored text with {@code {placeholders}}. */
    public static MutableComponent parse(String raw, Map<String, Component> placeholders) {
        return parse(raw, Allow.ALL, placeholders);
    }

    /** Text with only what {@code allow} permits honoured; the rest stays as typed. */
    public static MutableComponent parse(String raw, Allow allow) {
        return parse(raw, allow, Map.of());
    }

    public static MutableComponent parse(String raw, Allow allow, Map<String, Component> placeholders) {
        List<Token> tokens = new ArrayList<>();
        if (raw != null) {
            new Scanner(raw, allow, placeholders, tokens).run();
        }
        // Gradients need to know how many characters they span.
        for (Token token : tokens) {
            for (Frame frame : token.frames) {
                if (frame.gradient != null || frame.rainbow) {
                    frame.total++;
                }
            }
        }

        MutableComponent root = Component.empty();
        StringBuilder run = new StringBuilder();
        Style runStyle = null;
        for (Token token : tokens) {
            Style style = Style.EMPTY;
            for (Frame frame : token.frames) {
                style = apply(style, frame);
            }
            if (token.insert != null) {
                if (runStyle != null) {
                    root.append(Component.literal(run.toString()).withStyle(runStyle));
                    run.setLength(0);
                    runStyle = null;
                }
                root.append(Component.empty().withStyle(style).append(token.insert));
                continue;
            }
            if (runStyle != null && !style.equals(runStyle)) {
                root.append(Component.literal(run.toString()).withStyle(runStyle));
                run.setLength(0);
            }
            runStyle = style;
            run.appendCodePoint(token.codePoint);
        }
        if (runStyle != null) {
            root.append(Component.literal(run.toString()).withStyle(runStyle));
        }
        return root;
    }

    /** The text without any formatting, e.g. to measure a nickname. */
    public static String plain(String raw) {
        return parse(raw).getString();
    }

    private static Style apply(Style style, Frame frame) {
        if (frame.color != null) {
            return style.withColor(TextColor.fromRgb(frame.color));
        }
        if (frame.deco != null) {
            return frame.deco.apply(style, frame.decoOn);
        }
        if (frame.hover != null) {
            return style.withHoverEvent(frame.hover);
        }
        if (frame.click != null) {
            return style.withClickEvent(frame.click);
        }
        if (frame.gradient != null) {
            int index = frame.seen++;
            double t = frame.total <= 1 ? 0 : (double) index / (frame.total - 1);
            return style.withColor(TextColor.fromRgb(along(frame.gradient, t)));
        }
        if (frame.rainbow) {
            int index = frame.seen++;
            double t = frame.total <= 0 ? 0 : (double) index / frame.total;
            return style.withColor(TextColor.fromRgb(hue(frame.reversed ? 1 - t : t)));
        }
        return style;
    }

    private static final class Scanner {
        final String raw;
        final Allow allow;
        final Map<String, Component> placeholders;
        final List<Token> tokens;
        final List<Frame> stack = new ArrayList<>();

        Scanner(String raw, Allow allow, Map<String, Component> placeholders, List<Token> tokens) {
            this.raw = raw;
            this.allow = allow;
            this.placeholders = placeholders;
            this.tokens = tokens;
        }

        void run() {
            int i = 0;
            int length = raw.length();
            while (i < length) {
                char c = raw.charAt(i);
                if (c == '\\' && i + 1 < length && raw.charAt(i + 1) == '<') {
                    emit('<');
                    i += 2;
                    continue;
                }
                if (c == '<') {
                    int close = tagEnd(raw, i + 1);
                    if (close > i + 1 && tag(raw.substring(i + 1, close))) {
                        i = close + 1;
                        continue;
                    }
                }
                if (c == '{') {
                    int close = raw.indexOf('}', i + 1);
                    if (close > i + 1) {
                        Component value = placeholders.get(raw.substring(i + 1, close));
                        if (value != null) {
                            tokens.add(new Token(0, value, stack.toArray(new Frame[0])));
                            i = close + 1;
                            continue;
                        }
                    }
                    if (allow.hex && i + 8 < length && raw.charAt(i + 1) == '#' && raw.charAt(i + 8) == '}') {
                        Integer hex = hex(raw, i + 2);
                        if (hex != null) {
                            legacyColor(hex);
                            i += 9;
                            continue;
                        }
                    }
                }
                if ((c == '&' || c == '§') && i + 1 < length) {
                    int used = legacy(i);
                    if (used == -1) {
                        emit(raw.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (used > 0) {
                        i += used;
                        continue;
                    }
                }
                int codePoint = raw.codePointAt(i);
                emit(codePoint);
                i += Character.charCount(codePoint);
            }
        }

        void emit(int codePoint) {
            tokens.add(new Token(codePoint, null, stack.toArray(new Frame[0])));
        }

        /**
         * Handles a legacy code at {@code i}. Returns how many characters it used,
         * 0 when it is not a code (or not allowed), or -1 for {@code &&}.
         */
        int legacy(int i) {
            char next = Character.toLowerCase(raw.charAt(i + 1));
            if (next == '&' && raw.charAt(i) == '&') {
                return -1;
            }
            if (next == '#') {
                Integer hex = allow.hex ? hex(raw, i + 2) : null;
                if (hex != null) {
                    legacyColor(hex);
                    return 8;
                }
                return 0;
            }
            if (next == 'x') {
                if (!allow.hex) {
                    return 0;
                }
                StringBuilder digits = new StringBuilder();
                for (int j = 0; j < 6; j++) {
                    int pos = i + 2 + j * 2;
                    if (pos + 1 >= raw.length() || (raw.charAt(pos) != '&' && raw.charAt(pos) != '§')) {
                        return 0;
                    }
                    digits.append(raw.charAt(pos + 1));
                }
                Integer hex = hex(digits.toString(), 0);
                if (hex != null) {
                    legacyColor(hex);
                    return 14;
                }
                return 0;
            }
            String named = LEGACY_COLORS.get(next);
            if (named != null) {
                if (!allow.colors) {
                    return 0;
                }
                legacyColor(NAMED.get(named));
                return 2;
            }
            Deco deco = Deco.ofLegacy(next);
            if (deco != null) {
                if (!allow.decorations) {
                    return 0;
                }
                Frame frame = new Frame("&" + next, true);
                frame.deco = deco;
                frame.decoOn = true;
                stack.add(frame);
                return 2;
            }
            if (next == 'r' && (allow.colors || allow.decorations)) {
                stack.clear();
                return 2;
            }
            return 0;
        }

        /** A legacy color replaces earlier legacy colors and formats, like vanilla. */
        void legacyColor(int rgb) {
            stack.removeIf(frame -> frame.legacy);
            Frame frame = new Frame("&", true);
            frame.color = rgb;
            stack.add(frame);
        }

        /** Applies one tag body (between {@code <} and {@code >}); false when it is not an allowed tag. */
        boolean tag(String body) {
            List<String> parts = args(body.trim());
            if (parts.isEmpty()) {
                return false;
            }
            String name = parts.get(0).toLowerCase(Locale.ROOT);
            if (name.startsWith("/")) {
                String closing = name.substring(1);
                if (!isKnown(closing)) {
                    return false;
                }
                for (int j = stack.size() - 1; j >= 0; j--) {
                    if (!stack.get(j).legacy && (closing.isEmpty() || closes(stack.get(j), closing))) {
                        stack.remove(j);
                        return true;
                    }
                }
                return true;
            }
            if (name.equals("reset") && (allow.colors || allow.decorations)) {
                stack.clear();
                return true;
            }
            if ((name.equals("newline") || name.equals("br")) && allow.events) {
                emit('\n');
                return true;
            }
            boolean negate = name.startsWith("!");
            if (negate) {
                name = name.substring(1);
            }
            Deco deco = DECO_TAGS.get(name);
            if (deco != null) {
                if (!allow.decorations) {
                    return false;
                }
                Frame frame = new Frame(name, false);
                frame.deco = deco;
                frame.decoOn = !negate && !(parts.size() > 1 && parts.get(1).equalsIgnoreCase("false"));
                stack.add(frame);
                return true;
            }
            if (negate) {
                return false;
            }
            String colorSpec = name;
            if ((name.equals("color") || name.equals("colour") || name.equals("c")) && parts.size() > 1) {
                colorSpec = parts.get(1);
            }
            Integer color = color(colorSpec);
            if (color != null) {
                if (colorSpec.startsWith("#") ? !allow.hex : !allow.colors) {
                    return false;
                }
                Frame frame = new Frame(name, false);
                frame.color = color;
                stack.add(frame);
                return true;
            }
            if (name.equals("gradient")) {
                if (!allow.gradients) {
                    return false;
                }
                List<Integer> stops = new ArrayList<>();
                for (int j = 1; j < parts.size(); j++) {
                    Integer stop = color(parts.get(j));
                    if (stop != null) {
                        stops.add(stop);
                    }
                }
                if (stops.isEmpty()) {
                    stops.add(0xFFFFFF);
                    stops.add(0x000000);
                } else if (stops.size() == 1) {
                    stops.add(stops.get(0));
                }
                Frame frame = new Frame("gradient", false);
                frame.gradient = stops.stream().mapToInt(Integer::intValue).toArray();
                stack.add(frame);
                return true;
            }
            if (name.equals("rainbow")) {
                if (!allow.gradients) {
                    return false;
                }
                Frame frame = new Frame("rainbow", false);
                frame.rainbow = true;
                frame.reversed = parts.size() > 1 && parts.get(1).startsWith("!");
                stack.add(frame);
                return true;
            }
            if (name.equals("hover") && allow.events && parts.size() > 1) {
                // <hover:show_text:'text'> or the short <hover:'text'>.
                String text = parts.size() > 2 ? join(parts, 2) : parts.get(1);
                Frame frame = new Frame("hover", false);
                frame.hover = new HoverEvent.ShowText(parse(text, allow, placeholders));
                stack.add(frame);
                return true;
            }
            if (name.equals("click") && allow.events && parts.size() > 2) {
                ClickEvent click = click(parts.get(1).toLowerCase(Locale.ROOT), join(parts, 2));
                if (click == null) {
                    return false;
                }
                Frame frame = new Frame("click", false);
                frame.click = click;
                stack.add(frame);
                return true;
            }
            return false;
        }
    }

    private static ClickEvent click(String action, String value) {
        return switch (action) {
            case "run_command", "run" -> new ClickEvent.RunCommand(value);
            case "suggest_command", "suggest" -> new ClickEvent.SuggestCommand(value);
            case "copy_to_clipboard", "copy" -> new ClickEvent.CopyToClipboard(value);
            case "open_url", "url" -> {
                try {
                    URI uri = URI.create(value);
                    String scheme = uri.getScheme();
                    yield scheme != null && (scheme.equals("http") || scheme.equals("https"))
                            ? new ClickEvent.OpenUrl(uri) : null;
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    /** Index of the {@code >} closing a tag that starts at {@code from}, skipping quoted parts; -1 if none. */
    public static int tagEnd(String raw, int from) {
        char quote = 0;
        for (int i = from; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < raw.length()) {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '>') {
                return i;
            } else if (c == '<') {
                return -1;
            }
        }
        return -1;
    }

    /** Splits a tag body on {@code :} outside quotes; quotes are removed. */
    static List<String> args(String body) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < body.length()) {
                    current.append(body.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ':') {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    private static String join(List<String> parts, int from) {
        return String.join(":", parts.subList(from, parts.size()));
    }

    /** Whether closing tag {@code close} ends {@code open}: same name, alias, or any color for a color. */
    private static boolean closes(Frame open, String close) {
        String base = close.split(":")[0];
        if (open.tag.equals(base)) {
            return true;
        }
        if (open.deco != null) {
            return open.deco == DECO_TAGS.get(base);
        }
        return open.color != null && (base.equals("color") || base.equals("colour") || base.equals("c")
                || color(base) != null);
    }

    private static boolean isKnown(String name) {
        String base = name.split(":")[0];
        return base.isEmpty() || DECO_TAGS.containsKey(base) || color(base) != null || base.equals("gradient")
                || base.equals("rainbow") || base.equals("color") || base.equals("colour") || base.equals("c")
                || base.equals("hover") || base.equals("click");
    }

    /** Theme names usable as color tags ({@code <val>}, {@code <ok>}); vanilla names cannot be redefined. */
    private static volatile Map<String, Integer> theme = Map.of();

    public static void setTheme(Map<String, Integer> colors) {
        Map<String, Integer> copy = new HashMap<>();
        colors.forEach((name, rgb) -> {
            String key = name.toLowerCase(Locale.ROOT);
            if (!NAMED.containsKey(key) && !DECO_TAGS.containsKey(key)) {
                copy.put(key, rgb);
            }
        });
        theme = Map.copyOf(copy);
    }

    /** A named color, theme color or {@code #rrggbb}, or {@code null}. */
    public static Integer color(String spec) {
        if (spec == null) {
            return null;
        }
        String s = spec.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("#") && s.length() == 7) {
            return hex(s, 1);
        }
        Integer named = NAMED.get(s);
        return named != null ? named : theme.get(s);
    }

    private static Integer hex(String raw, int offset) {
        if (offset + 6 > raw.length()) {
            return null;
        }
        int rgb = 0;
        for (int j = 0; j < 6; j++) {
            int digit = Character.digit(raw.charAt(offset + j), 16);
            if (digit < 0) {
                return null;
            }
            rgb = (rgb << 4) | digit;
        }
        return rgb;
    }

    /** Color at {@code t} (0..1) along evenly spaced gradient stops. */
    static int along(int[] stops, double t) {
        double scaled = Math.max(0, Math.min(1, t)) * (stops.length - 1);
        int from = Math.min((int) Math.floor(scaled), stops.length - 2);
        double local = scaled - from;
        int a = stops[from];
        int b = stops[from + 1];
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * local);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * local);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * local);
        return (r << 16) | (g << 8) | bl;
    }

    /** Fully saturated rainbow color at {@code t} (0..1) around the hue circle, red first. */
    static int hue(double t) {
        double h = ((t % 1.0) + 1.0) % 1.0 * 6.0;
        int sector = (int) Math.floor(h) % 6;
        int rising = (int) Math.round((h - Math.floor(h)) * 255);
        int falling = 255 - rising;
        return switch (sector) {
            case 0 -> 0xFF0000 | rising << 8;
            case 1 -> falling << 16 | 0x00FF00;
            case 2 -> 0x00FF00 | rising;
            case 3 -> falling << 8 | 0x0000FF;
            case 4 -> rising << 16 | 0x0000FF;
            default -> 0xFF0000 | falling;
        };
    }
}
