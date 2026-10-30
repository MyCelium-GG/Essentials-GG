package my.celium.org.essentialsgg.cmd;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import my.celium.org.essentialsgg.EssentialsGG;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import my.celium.org.logging.MycelLog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command registration. Each command is a {@link Def}: a name, the module it
 * belongs to, its permission node with a default level, default aliases and
 * the Brigadier tree. {@code config/essentialsgg/commands.json} lists every
 * command and lets admins turn single commands off, change aliases and add a
 * cooldown.
 *
 * <p>Vanilla and other mods win name clashes: a label someone else already
 * registered is skipped (and logged), never overridden. A command whose own
 * name is taken moves under {@code /ess <name>}, so nothing is lost.
 */
public final class Cmd {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final List<Def> DEFS = new ArrayList<>();
    /** Command name -> player -> when its cooldown ends (millis). */
    private static final Map<String, Map<UUID, Long>> COOLDOWNS = new HashMap<>();
    private static Map<String, Settings> settings = new LinkedHashMap<>();

    /** One command's line in {@code commands.json}. */
    public static final class Settings {
        public boolean enabled = true;
        public List<String> aliases = new ArrayList<>();
        /** Seconds between two successful uses per player; staff with {@code essentialsgg.bypass.cooldown} skip it. */
        public int cooldown;
    }

    /** Wraps command bodies with the cooldown and error handling of one command. */
    public static final class Builder {
        private final Def def;

        Builder(Def def) {
            this.def = def;
        }

        public Command<CommandSourceStack> run(Command<CommandSourceStack> body) {
            return context -> execute(def, body, context);
        }
    }

    public record Def(String name, Module module, String perm, int level, List<String> aliases,
            Body body) {
    }

    @FunctionalInterface
    public interface Body {
        LiteralArgumentBuilder<CommandSourceStack> build(LiteralArgumentBuilder<CommandSourceStack> root, Builder b);
    }

    private Cmd() {
    }

    /**
     * Declares a command. {@code perm} is the node after {@code essentialsgg.}
     * and gets {@code level} as its default operator level.
     */
    public static void define(String name, Module module, String perm, int level, List<String> aliases, Body body) {
        String node = Perms.declare(Perms.ROOT + perm, level);
        DEFS.add(new Def(name, module, node, level, aliases, body));
    }

    public static void define(String name, Module module, int level, Body body) {
        define(name, module, "command." + name, level, List.of(), body);
    }

    public static List<Def> all() {
        return List.copyOf(DEFS);
    }

    /** Reads {@code commands.json}, adds lines for new commands, writes it back. */
    public static void loadSettings(Path configDir) {
        Path file = configDir.resolve(EssentialsGG.ID).resolve("commands.json");
        Map<String, Settings> read = new LinkedHashMap<>();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, Settings> parsed = GSON.fromJson(JsonParser.parseReader(reader),
                        new TypeToken<LinkedHashMap<String, Settings>>() { }.getType());
                if (parsed != null) {
                    read.putAll(parsed);
                }
            } catch (IOException | RuntimeException e) {
                MycelLog.warnMod(EssentialsGG.ID, "Ignoring broken {}: {}", file, e.toString());
                return;
            }
        }
        Map<String, Settings> merged = new LinkedHashMap<>();
        for (Def def : DEFS) {
            Settings s = read.get(def.name);
            if (s == null) {
                s = new Settings();
                s.aliases = new ArrayList<>(def.aliases);
            }
            if (s.aliases == null) {
                s.aliases = new ArrayList<>();
            }
            merged.put(def.name, s);
        }
        settings = merged;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(merged), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MycelLog.warnMod(EssentialsGG.ID, "Could not write {}: {}", file, e.toString());
        }
    }

    public static boolean isEnabled(String name) {
        Settings s = settings.get(name);
        return s == null || s.enabled;
    }

    /** Whether {@code player} could run the command {@code name}: registered, switched on and permitted. */
    public static boolean usable(ServerPlayer player, String name) {
        for (Def def : DEFS) {
            if (def.name.equals(name)) {
                return def.module.enabled() && isEnabled(name) && Perms.has(player, def.perm);
            }
        }
        return false;
    }

    /**
     * Registers every enabled command of every enabled module. {@code /ess}
     * gets its own subcommands plus only the commands whose name was taken,
     * so its suggestions stay short.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> ess = Commands.literal("ess");
        List<String> skipped = new ArrayList<>();
        for (Def def : DEFS) {
            if (!def.module.enabled() || !isEnabled(def.name) || def.name.equals("ess")) {
                continue;
            }
            List<String> labels = new ArrayList<>();
            labels.add(def.name);
            Settings s = settings.get(def.name);
            for (String alias : s != null ? s.aliases : def.aliases) {
                String label = alias.toLowerCase(Locale.ROOT).trim();
                if (!label.isEmpty() && !labels.contains(label)) {
                    labels.add(label);
                }
            }
            boolean ownName = false;
            for (String label : labels) {
                if (label.equals("ess") || dispatcher.getRoot().getChild(label) != null) {
                    skipped.add(label);
                    continue;
                }
                dispatcher.register(build(def, label));
                ownName |= label.equals(def.name);
            }
            if (!ownName) {
                ess.then(build(def, def.name));
            }
        }
        // The /ess root itself carries the admin subcommands (see Admin).
        for (Def def : DEFS) {
            if (def.name.equals("ess")) {
                ess = def.body.build(ess, new Builder(def));
            }
        }
        dispatcher.register(ess);
        if (!skipped.isEmpty()) {
            MycelLog.infoMod(EssentialsGG.ID, "Left to vanilla/other mods: {} (commands whose own name is taken are under /ess)",
                    String.join(", ", skipped));
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(Def def, String label) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(label)
                .requires(source -> Perms.has(source, def.perm));
        return def.body.build(root, new Builder(def));
    }

    private static int execute(Def def, Command<CommandSourceStack> body, CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        Settings s = settings.get(def.name);
        int cooldown = s == null ? 0 : s.cooldown;
        long now = System.currentTimeMillis();
        boolean cooled = cooldown > 0 && player != null && !Perms.has(player, EssentialsGG.BYPASS_COOLDOWN);
        if (cooled) {
            Long until = COOLDOWNS.getOrDefault(def.name, Map.of()).get(player.getUUID());
            if (until != null && until > now) {
                Lang.send(player, "error.cooldown", "time", Time.format(player, until - now));
                return 0;
            }
        }
        int result;
        try {
            result = body.run(context);
        } catch (CommandSyntaxException e) {
            throw e;
        } catch (RuntimeException e) {
            MycelLog.errorMod(EssentialsGG.ID, "/" + def.name + " failed", e);
            Lang.send(context.getSource(), "error.internal");
            return 0;
        }
        if (cooled && result > 0) {
            COOLDOWNS.computeIfAbsent(def.name, k -> new HashMap<>()).put(player.getUUID(), now + cooldown * 1000L);
        }
        return result;
    }

    /** Small helper for bodies that only need a player: {@code b.run(Cmd.self(Homes::list))}. */
    public static Command<CommandSourceStack> self(PlayerCommand body) {
        return context -> body.run(context.getSource().getPlayerOrException(), context);
    }

    @FunctionalInterface
    public interface PlayerCommand {
        int run(ServerPlayer player, CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }
}
