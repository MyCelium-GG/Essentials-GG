package my.celium.org.essentialsgg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import my.celium.org.Mycel;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import my.celium.org.testing.MycelTesting;

/** The mod boots, every command is defined once, and every message the code sends exists in every language. */
class EssentialsGGTest {
    /** Keys built at runtime, which the source scan cannot see. */
    private static final List<String> DYNAMIC_KEYS = List.of("menu.rtp_overworld", "menu.rtp_nether", "menu.rtp_end",
            "util.fly_on_other", "util.fly_off_other", "util.god_on_other", "util.god_off_other", "util.healed_other",
            "util.fed_other", "util.gamemode_other", "util.extinguished_other");
    /** String literals in Lang calls: the first literal after the target (player, source, lang). */
    private static final Pattern KEY = Pattern.compile(
            "Lang\\.(?:send|get|raw|broadcast|enabled)\\([^\"\\n]*?\"([a-z][a-z_]*(?:\\.[a-z_]+)?)\"");
    /** Ternaries choosing between two keys: {@code cond ? "a.b" : "a.c"}. */
    private static final Pattern CHOICE = Pattern.compile("\\? \"([a-z_]+\\.[a-z_]+)\" : \"([a-z_]+\\.[a-z_]+)\"");

    @TempDir
    static Path configDir;

    @BeforeAll
    static void initOnce() {
        MycelTesting.useConfigDir(configDir);
        EssentialsGG.init();
    }

    @Test
    void registersWithMycel() {
        assertTrue(Mycel.getRegisteredMods().contains(EssentialsGG.META));
        assertEquals("essentialsgg", EssentialsGG.META.id());
        assertTrue(EssentialsGG.enabled.get());
    }

    @Test
    void commandsAreUniqueAndWritten() {
        Set<String> labels = new HashSet<>();
        for (Cmd.Def def : Cmd.all()) {
            assertTrue(def.name().matches("[a-z]+"), def.name());
            assertTrue(labels.add(def.name()), "defined twice: " + def.name());
            assertTrue(def.perm().startsWith(Perms.ROOT), def.perm());
        }
        assertTrue(labels.size() > 80, "commands: " + labels.size());
        assertTrue(Files.exists(configDir.resolve("essentialsgg").resolve("commands.json")));
    }

    @Test
    void sidebarAndDiscordStartOff() {
        assertFalse(Module.SIDEBAR.enabled());
        assertFalse(Module.DISCORD.enabled());
        assertTrue(Module.HOMES.enabled());
    }

    @Test
    void everyUsedKeyExistsInEveryLanguage() throws IOException {
        Set<String> used = new TreeSet<>(DYNAMIC_KEYS);
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher keys = KEY.matcher(source);
                while (keys.find()) {
                    used.add(keys.group(1));
                }
                Matcher choices = CHOICE.matcher(source);
                while (choices.find()) {
                    used.add(choices.group(1));
                    used.add(choices.group(2));
                }
            }
        }
        used.removeIf(key -> key.startsWith("menu.rtp_") && !DYNAMIC_KEYS.contains(key));
        assertTrue(used.size() > 150, "keys found: " + used.size());
        for (String code : Lang.BUNDLED) {
            JsonObject messages = bundled(code);
            Set<String> missing = new TreeSet<>();
            for (String key : used) {
                if (!messages.has(key)) {
                    missing.add(key);
                }
            }
            assertEquals(Set.of(), missing, code + " is missing keys");
        }
    }

    @Test
    void translationsHaveTheSameKeysAsEnglish() throws IOException {
        Set<String> english = bundled(Lang.DEFAULT).keySet();
        for (String code : Lang.BUNDLED) {
            assertEquals(new TreeSet<>(english), new TreeSet<>(bundled(code).keySet()), code);
        }
    }

    private static JsonObject bundled(String code) throws IOException {
        try (InputStream in = Lang.class.getResourceAsStream("/essentialsgg/lang/" + code + ".json")) {
            assertTrue(in != null, code + ".json is not shipped");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
