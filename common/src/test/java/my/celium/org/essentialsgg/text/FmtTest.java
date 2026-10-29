package my.celium.org.essentialsgg.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

class FmtTest {
    /** One entry per styled run: its text and style. */
    private record Run(String text, Style style) {
        int color() {
            return style.getColor() == null ? -1 : style.getColor().getValue();
        }
    }

    private static List<Run> runs(Component parsed) {
        List<Run> runs = new ArrayList<>();
        for (Component part : parsed.getSiblings()) {
            runs.add(new Run(part.getString(), part.getStyle()));
        }
        return runs;
    }

    private static List<Run> runs(String raw) {
        return runs(Fmt.parse(raw));
    }

    @Test
    void plainTextKeepsNoStyle() {
        List<Run> runs = runs("hello");
        assertEquals(1, runs.size());
        assertEquals(-1, runs.get(0).color());
        assertEquals("", Fmt.parse(null).getString());
    }

    @Test
    void legacyAndHexCodes() {
        assertEquals(0xFFAA00, runs("&6gold").get(0).color());
        assertEquals(0xFF55AA, runs("&#ff55aapink").get(0).color());
        assertEquals(0xFF55AA, runs("{#ff55aa}pink").get(0).color());
        assertEquals(0xFF55AA, runs("&x&f&f&5&5&a&apink").get(0).color());
        assertEquals("Salt & Pepper", Fmt.plain("Salt && Pepper"));
    }

    @Test
    void tagsAndGradients() {
        List<Run> runs = runs("<red>a</red>b");
        assertEquals(0xFF5555, runs.get(0).color());
        assertEquals(-1, runs.get(1).color());
        List<Run> gradient = runs("<gradient:#000000:#ffffff>abc</gradient>");
        assertEquals(3, gradient.size());
        assertEquals(0x000000, gradient.get(0).color());
        assertEquals(0xFFFFFF, gradient.get(2).color());
        assertEquals(6, runs("<rainbow>abcdef").size());
        assertTrue(runs("<b>bold").get(0).style().isBold());
    }

    @Test
    void clickAndHoverEvents() {
        Component parsed = Fmt.parse("<click:run_command:'/spawn'><hover:show_text:'Go <red>home'>spawn</hover></click>");
        Style style = runs(parsed).get(0).style();
        assertInstanceOf(ClickEvent.RunCommand.class, style.getClickEvent());
        assertEquals("/spawn", ((ClickEvent.RunCommand) style.getClickEvent()).command());
        HoverEvent.ShowText hover = assertInstanceOf(HoverEvent.ShowText.class, style.getHoverEvent());
        assertEquals("Go home", hover.value().getString());
        assertNull(runs(Fmt.parse("<click:open_url:'javascript:alert(1)'>x")).get(0).style().getClickEvent());
    }

    @Test
    void allowLimitsWhatPlayersCanUse() {
        Fmt.Allow colorsOnly = new Fmt.Allow(true, false, false, false, false);
        assertEquals(0xFF5555, runs(Fmt.parse("&cred", colorsOnly)).get(0).color());
        assertEquals("&#ff0000x", Fmt.parse("&#ff0000x", colorsOnly).getString());
        assertEquals("<gradient:red:blue>x", Fmt.parse("<gradient:red:blue>x", colorsOnly).getString());
        assertEquals("<click:run_command:'/op me'>x", Fmt.parse("<click:run_command:'/op me'>x", Fmt.Allow.STYLE).getString());
        assertEquals("&lbold", Fmt.parse("&lbold", Fmt.Allow.NONE).getString());
    }

    @Test
    void placeholdersAreInsertedNotParsed() {
        Component parsed = Fmt.parse("<red>Hi {name}!", Map.of("name", Component.literal("<b>&cEvil")));
        assertEquals("Hi <b>&cEvil!", parsed.getString());
        assertEquals("{unknown}", Fmt.parse("{unknown}").getString());
    }

    @Test
    void themeColorsAreTags() {
        Fmt.setTheme(Map.of("val", 0x123456, "red", 0x000001));
        assertEquals(0x123456, runs("<val>x</val>y").get(0).color());
        assertEquals(-1, runs("<val>x</val>y").get(1).color());
        // Vanilla names cannot be redefined.
        assertNotEquals(0x000001, runs("<red>x").get(0).color());
    }

    @Test
    void placeholdersInsideTagsBecomeSafeText() {
        Map<String, Component> values = Map.of("name", Component.literal("Steve'><red>"));
        String inlined = Lang.inTags("<click:run_command:'/tpaccept {name}'>[accept]</click> {name}", values);
        assertEquals("<click:run_command:'/tpaccept Stevered'>[accept]</click> {name}", inlined);
    }
}
