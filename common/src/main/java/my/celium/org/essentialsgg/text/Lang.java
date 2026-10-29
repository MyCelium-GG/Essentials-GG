package my.celium.org.essentialsgg.text;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import my.celium.org.essentialsgg.EssentialsGG;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.logging.MycelLog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Every message the mod sends, per language. The jar ships
 * {@code essentialsgg/lang/<code>.json}; on start each is copied to
 * {@code config/essentialsgg/lang/}, where admins edit them or drop in new
 * languages. Unedited copies follow updates (see {@link #refreshShipped}). Keys missing from an edited file come from the shipped
 * copy, so updates never leave holes. Each player reads their own language
 * ({@code /language}), else their client's, else {@link #DEFAULT}.
 *
 * <p>Messages are {@link Fmt} text with {@code {placeholders}}; {@code {prefix}}
 * is always available.
 */
public final class Lang {
    public static final String DEFAULT = "en_us";
    /** Languages shipped in the jar. */
    public static final List<String> BUNDLED = List.of("en_us", "de_de", "es_es", "fr_fr", "nl_nl", "pt_br");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private static volatile Map<String, Map<String, String>> languages = Map.of();

    private Lang() {
    }

    /** (Re)loads every language from the config folder, copying shipped files there first. */
    public static synchronized void load(Path configDir) {
        Path dir = configDir.resolve(EssentialsGG.ID).resolve("lang");
        Map<String, Map<String, String>> loaded = new HashMap<>();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            MycelLog.warnMod(EssentialsGG.ID, "Could not create {}: {}", dir, e.toString());
        }
        Map<String, String> fallback = bundled(DEFAULT);
        refreshShipped(dir);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "[a-z]*.json")) {
            for (Path file : files) {
                String code = file.getFileName().toString().replace(".json", "").toLowerCase(Locale.ROOT);
                Map<String, String> messages = new LinkedHashMap<>(fallback);
                messages.putAll(bundled(code));
                messages.putAll(read(file));
                loaded.put(code, messages);
            }
        } catch (IOException e) {
            MycelLog.warnMod(EssentialsGG.ID, "Could not list {}: {}", dir, e.toString());
        }
        loaded.putIfAbsent(DEFAULT, fallback);
        languages = Map.copyOf(loaded);
    }

    /**
     * Copies each shipped language to the config folder. A copy nobody edited
     * (same hash as when it was written, kept in {@code .shipped.json}) is
     * replaced by the new version, so updates reach it; an edited one is
     * left alone. A copy from before hashes were kept is renamed to
     * {@code <code>.json.old} and replaced.
     */
    private static void refreshShipped(Path dir) {
        Path hashFile = dir.resolve(".shipped.json");
        Map<String, String> hashes = new LinkedHashMap<>(Files.exists(hashFile) ? read(hashFile) : Map.of());
        boolean changed = false;
        for (String code : BUNDLED) {
            Map<String, String> shipped = bundled(code);
            if (shipped.isEmpty()) {
                continue;
            }
            String content = GSON.toJson(shipped);
            Path file = dir.resolve(code + ".json");
            try {
                if (Files.exists(file)) {
                    String current = Files.readString(file, StandardCharsets.UTF_8);
                    String known = hashes.get(code);
                    if (current.equals(content) || (known != null && !known.equals(sha256(current)))) {
                        continue;
                    }
                    if (known == null) {
                        Files.move(file, dir.resolve(code + ".json.old"), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Files.writeString(file, content, StandardCharsets.UTF_8);
                hashes.put(code, sha256(content));
                changed = true;
            } catch (IOException e) {
                MycelLog.warnMod(EssentialsGG.ID, "Could not write {}: {}", file, e.toString());
            }
        }
        if (changed) {
            try {
                Files.writeString(hashFile, GSON.toJson(hashes), StandardCharsets.UTF_8);
            } catch (IOException e) {
                MycelLog.warnMod(EssentialsGG.ID, "Could not write {}: {}", hashFile, e.toString());
            }
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Codes of every loaded language, sorted. */
    public static List<String> available() {
        return new ArrayList<>(new TreeSet<>(languages.keySet()));
    }

    public static boolean exists(String code) {
        return languages.containsKey(code.toLowerCase(Locale.ROOT));
    }

    /** The language {@code player} reads: their choice, else their client's, else the default. */
    public static String languageOf(ServerPlayer player) {
        if (player == null) {
            return DEFAULT;
        }
        PlayerData data = Store.get(player.getUUID());
        if (data != null && data.lang != null && languages.containsKey(data.lang)) {
            return data.lang;
        }
        String client = player.clientInformation().language().toLowerCase(Locale.ROOT);
        if (languages.containsKey(client)) {
            return client;
        }
        // de_at reads de_de rather than English.
        int underscore = client.indexOf('_');
        if (underscore > 0) {
            String family = client.substring(0, underscore + 1);
            for (String code : languages.keySet()) {
                if (code.startsWith(family)) {
                    return code;
                }
            }
        }
        return DEFAULT;
    }

    /** The raw template, or a visible marker when the key is missing. */
    public static String raw(String lang, String key) {
        Map<String, String> messages = languages.get(lang);
        String value = messages == null ? null : messages.get(key);
        if (value == null) {
            Map<String, String> fallback = languages.get(DEFAULT);
            value = fallback == null ? null : fallback.get(key);
        }
        return value != null ? value : "<red>[missing message: " + key + "]";
    }

    public static String raw(ServerPlayer player, String key) {
        return raw(languageOf(player), key);
    }

    /** Whether {@code key} has a non-empty message (empty messages mean "say nothing"). */
    public static boolean enabled(ServerPlayer player, String key) {
        return !raw(languageOf(player), key).isEmpty();
    }

    /**
     * The message {@code key} for {@code player} (console: default language),
     * {@code args} as name/value pairs. Values are inserted, never parsed:
     * Components keep their style, everything else becomes plain text.
     */
    public static MutableComponent get(ServerPlayer player, String key, Object... args) {
        return get(languageOf(player), key, args);
    }

    public static MutableComponent get(String lang, String key, Object... args) {
        Map<String, Component> values = placeholders(lang, args);
        return Fmt.parse(inTags(raw(lang, key), values), values);
    }

    /**
     * Placeholders inside a tag ({@code <click:run_command:'/tpaccept {name}'>})
     * become plain text before parsing, stripped of anything that could end
     * the tag. Placeholders in the text itself stay components.
     */
    static String inTags(String template, Map<String, Component> values) {
        if (template.indexOf('{') < 0) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length());
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            int end = c == '<' ? Fmt.tagEnd(template, i + 1) : -1;
            if (end < 0) {
                out.append(c);
                i++;
                continue;
            }
            String tag = template.substring(i, end + 1);
            for (Map.Entry<String, Component> entry : values.entrySet()) {
                String token = "{" + entry.getKey() + "}";
                if (tag.contains(token)) {
                    tag = tag.replace(token, entry.getValue().getString().replaceAll("[<>'\"\\\\]", ""));
                }
            }
            out.append(tag);
            i = end + 1;
        }
        return out.toString();
    }

    private static Map<String, Component> placeholders(String lang, Object... args) {
        Map<String, Component> map = new HashMap<>();
        String prefix = raw(lang, "prefix");
        map.put("prefix", Fmt.parse(prefix));
        for (int i = 0; i + 1 < args.length; i += 2) {
            map.put(String.valueOf(args[i]), value(args[i + 1]));
        }
        return map;
    }

    public static Component value(Object value) {
        if (value instanceof Component component) {
            return component;
        }
        if (value instanceof Double d) {
            return Component.literal(d == Math.rint(d) ? String.valueOf(d.longValue()) : String.format(Locale.ROOT, "%.2f", d));
        }
        return Component.literal(String.valueOf(value));
    }

    /** Sends {@code key} to a player; empty messages are skipped. */
    public static void send(ServerPlayer player, String key, Object... args) {
        if (player != null && enabled(player, key)) {
            player.sendSystemMessage(get(player, key, args));
        }
    }

    /** Sends {@code key} to whoever ran a command, in their language. */
    public static void send(CommandSourceStack source, String key, Object... args) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            send(player, key, args);
        } else if (!raw(DEFAULT, key).isEmpty()) {
            source.sendSystemMessage(get(DEFAULT, key, args));
        }
    }

    /** Sends {@code key} to every online player in their own language, and logs it to the console. */
    public static void broadcast(MinecraftServer server, String key, Object... args) {
        if (raw(DEFAULT, key).isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, key, args);
        }
        server.sendSystemMessage(get(DEFAULT, key, args));
    }

    private static Map<String, String> bundled(String code) {
        try (InputStream in = Lang.class.getResourceAsStream("/essentialsgg/lang/" + code + ".json")) {
            if (in == null) {
                return new LinkedHashMap<>();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return flatten(JsonParser.parseReader(reader));
            }
        } catch (IOException | RuntimeException e) {
            MycelLog.warnMod(EssentialsGG.ID, "Broken shipped language {}: {}", code, e.toString());
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, String> read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return flatten(JsonParser.parseReader(reader));
        } catch (IOException | RuntimeException e) {
            MycelLog.warnMod(EssentialsGG.ID, "Ignoring broken language file {}: {}", file, e.toString());
            return new LinkedHashMap<>();
        }
    }

    /** Language files are flat {@code "key": "message"} maps; a list of strings joins into lines. */
    private static Map<String, String> flatten(JsonElement root) {
        Map<String, String> out = new LinkedHashMap<>();
        if (root == null || !root.isJsonObject()) {
            return out;
        }
        JsonObject object = root.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                out.put(entry.getKey(), value.getAsString());
            } else if (value.isJsonArray()) {
                List<String> lines = new ArrayList<>();
                value.getAsJsonArray().forEach(line -> lines.add(line.getAsString()));
                out.put(entry.getKey(), String.join("<newline>", lines));
            }
        }
        return out;
    }
}
