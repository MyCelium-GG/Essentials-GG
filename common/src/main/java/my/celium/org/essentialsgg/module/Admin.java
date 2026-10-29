package my.celium.org.essentialsgg.module;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import my.celium.org.Mycel;
import my.celium.org.essentialsgg.EssentialsGG;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.data.Loc;
import my.celium.org.essentialsgg.data.MiniYaml;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.ServerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import my.celium.org.logging.MycelLog;
import my.celium.org.schedule.MycelScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ess} itself: {@code help}, {@code reload}, {@code version}, {@code modules},
 * {@code migrate essentialsx [path] [confirm]}; plus {@code /language}.
 *
 * <p>The EssentialsX importer reads {@code userdata/*.yml} (homes, nicknames,
 * money, last name and location, mutes), {@code warps/*.yml} and the
 * EssentialsSpawn {@code spawn.yml}. Without {@code confirm} it only reports
 * what it would import. Players already known here keep their data; their
 * missing homes are added. Bans need no import: copy Paper's
 * {@code banned-players.json} over, it is the same file.
 */
public final class Admin {
    private static final String RELOAD = Perms.declare(Perms.ROOT + "admin.reload", 3);
    private static final String MIGRATE = Perms.declare(Perms.ROOT + "admin.migrate", 4);

    private Admin() {
    }

    public static void commands() {
        Cmd.define("ess", Module.CORE, "command.ess", 0, List.of(), (root, b) -> root
                .executes(b.run(c -> version(c.getSource())))
                .then(Commands.literal("help").executes(b.run(c -> {
                    Lang.send(c.getSource(), "admin.help");
                    return 1;
                })))
                .then(Commands.literal("version").executes(b.run(c -> version(c.getSource()))))
                .then(Commands.literal("reload").requires(s -> Perms.has(s, RELOAD))
                        .executes(b.run(c -> {
                            EssentialsGG.reload();
                            Lang.send(c.getSource(), "admin.reloaded");
                            return 1;
                        })))
                .then(Commands.literal("modules").executes(b.run(c -> modules(c.getSource()))))
                .then(Commands.literal("migrate").requires(s -> Perms.has(s, MIGRATE))
                        .then(Commands.literal("essentialsx")
                                .executes(b.run(c -> migrate(c, null, false)))
                                .then(Commands.literal("confirm").executes(b.run(c -> migrate(c, null, true))))
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .executes(b.run(c -> migrate(c, StringArgumentType.getString(c, "path"), false)))
                                        .then(Commands.literal("confirm").executes(b.run(c ->
                                                migrate(c, StringArgumentType.getString(c, "path"), true))))))));
        Cmd.define("language", Module.CORE, "command.language", 0, List.of("lang"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> {
                    Lang.send(p, "admin.language_list", "current", Lang.languageOf(p),
                            "languages", String.join(", ", Lang.available()));
                    return 1;
                })))
                .then(Commands.argument("code", StringArgumentType.word())
                        .suggests((c, s) -> {
                            s.suggest("auto");
                            Lang.available().forEach(s::suggest);
                            return s.buildFuture();
                        })
                        .executes(b.run(Cmd.self((p, c) -> language(p, StringArgumentType.getString(c, "code")))))));
    }

    private static int version(CommandSourceStack source) {
        Lang.send(source, "admin.version", "version", EssentialsGG.META.version(), "loader", Mycel.getPlatform().displayName(),
                "minecraft", Mycel.getMinecraftVersion());
        return 1;
    }

    private static int modules(CommandSourceStack source) {
        for (Module module : Module.values()) {
            if (module != Module.CORE) {
                Lang.send(source, module.enabled() ? "admin.module_on" : "admin.module_off", "module", module.key());
            }
        }
        return 1;
    }

    private static int language(ServerPlayer player, String code) {
        PlayerData data = Store.of(player);
        String lower = code.toLowerCase(Locale.ROOT);
        if (lower.equals("auto")) {
            data.lang = null;
        } else if (Lang.exists(lower)) {
            data.lang = lower;
        } else {
            Lang.send(player, "admin.language_unknown", "code", code, "languages", String.join(", ", Lang.available()));
            return 0;
        }
        Store.markDirty();
        Lang.send(player, "admin.language_set", "code", Lang.languageOf(player));
        return 1;
    }

    // ---------------------------------------------------------------- EssentialsX import

    /** What an import found, before anything is written. */
    private static final class Found {
        final List<PlayerData> players = new ArrayList<>();
        final Map<String, Loc> warps = new LinkedHashMap<>();
        Loc spawn;
        int homes;
        int broken;
    }

    private static int migrate(CommandContext<CommandSourceStack> context, String path, boolean apply) {
        CommandSourceStack source = context.getSource();
        Path dir = path != null ? Path.of(path) : null;
        if (dir == null) {
            for (Path candidate : List.of(Path.of("plugins", "Essentials"),
                    Mycel.getConfigDir().resolve(EssentialsGG.ID).resolve("import").resolve("Essentials"))) {
                if (Files.isDirectory(candidate)) {
                    dir = candidate;
                    break;
                }
            }
        }
        if (dir == null || !Files.isDirectory(dir)) {
            Lang.send(source, "admin.migrate_missing", "path", path != null ? path : "plugins/Essentials");
            return 0;
        }
        Path folder = dir;
        Lang.send(source, "admin.migrate_reading", "path", folder.toString());
        MycelScheduler.runAsync(() -> {
            Found found = read(folder);
            MycelScheduler.runOnServer(() -> finish(source, found, apply));
        });
        return 1;
    }

    private static Found read(Path dir) {
        Found found = new Found();
        Path users = dir.resolve("userdata");
        if (Files.isDirectory(users)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(users, "*.yml")) {
                for (Path file : files) {
                    try {
                        PlayerData data = user(file);
                        if (data != null) {
                            found.players.add(data);
                            found.homes += data.homes.size();
                        }
                    } catch (IOException | RuntimeException e) {
                        found.broken++;
                    }
                }
            } catch (IOException e) {
                MycelLog.warnMod(EssentialsGG.ID, "Could not list {}: {}", users, e.toString());
            }
        }
        Path warps = dir.resolve("warps");
        if (Files.isDirectory(warps)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(warps, "*.yml")) {
                for (Path file : files) {
                    try {
                        Map<String, Object> yaml = MiniYaml.parse(Files.readString(file, StandardCharsets.UTF_8));
                        Loc loc = loc(yaml);
                        String name = MiniYaml.string(yaml, "name");
                        if (name == null) {
                            name = file.getFileName().toString().replace(".yml", "");
                        }
                        if (loc != null) {
                            found.warps.put(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_"), loc);
                        }
                    } catch (IOException | RuntimeException e) {
                        found.broken++;
                    }
                }
            } catch (IOException e) {
                MycelLog.warnMod(EssentialsGG.ID, "Could not list {}: {}", warps, e.toString());
            }
        }
        Path spawn = dir.resolve("spawn.yml");
        if (Files.isRegularFile(spawn)) {
            try {
                Map<String, Object> yaml = MiniYaml.parse(Files.readString(spawn, StandardCharsets.UTF_8));
                found.spawn = loc(MiniYaml.map(MiniYaml.map(yaml, "spawns"), "default"));
            } catch (IOException | RuntimeException e) {
                found.broken++;
            }
        }
        return found;
    }

    private static PlayerData user(Path file) throws IOException {
        String base = file.getFileName().toString().replace(".yml", "");
        UUID id;
        try {
            id = UUID.fromString(base);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Map<String, Object> yaml = MiniYaml.parse(Files.readString(file, StandardCharsets.UTF_8));
        PlayerData data = new PlayerData(id, MiniYaml.string(yaml, "last-account-name"));
        if (data.name == null) {
            return null;
        }
        data.balance = MiniYaml.number(yaml, "money", 0);
        String nick = MiniYaml.string(yaml, "nickname");
        if (nick != null && !nick.isBlank()) {
            data.nick = nick;
        }
        for (Map.Entry<String, Object> home : MiniYaml.map(yaml, "homes").entrySet()) {
            if (home.getValue() instanceof Map<?, ?>) {
                Loc loc = loc(MiniYaml.map(MiniYaml.map(yaml, "homes"), home.getKey()));
                String name = home.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
                if (loc != null) {
                    data.homes.put(name, loc);
                }
            }
        }
        data.logout = loc(MiniYaml.map(yaml, "logoutlocation"));
        Map<String, Object> timestamps = MiniYaml.map(yaml, "timestamps");
        data.lastSeen = (long) MiniYaml.number(timestamps, "logout", 0);
        data.lastIp = MiniYaml.string(yaml, "ip-address");
        if ("true".equals(MiniYaml.string(yaml, "muted"))) {
            long until = (long) MiniYaml.number(timestamps, "mute", 0);
            String reason = MiniYaml.string(yaml, "mute-reason");
            data.mute = new PlayerData.Mute(until, reason == null ? "" : reason, "EssentialsX");
        }
        return data;
    }

    /** A Bukkit location section; world names map to vanilla dimensions. */
    private static Loc loc(Map<String, Object> yaml) {
        String world = MiniYaml.string(yaml, "world-name");
        if (world == null) {
            world = MiniYaml.string(yaml, "world");
        }
        if (world == null || !yaml.containsKey("x")) {
            return null;
        }
        String dim;
        if (world.endsWith("_nether")) {
            dim = "minecraft:the_nether";
        } else if (world.endsWith("_the_end")) {
            dim = "minecraft:the_end";
        } else if (world.contains(":")) {
            dim = world;
        } else {
            dim = "minecraft:overworld";
        }
        return new Loc(dim, MiniYaml.number(yaml, "x", 0), MiniYaml.number(yaml, "y", 64), MiniYaml.number(yaml, "z", 0),
                (float) MiniYaml.number(yaml, "yaw", 0), (float) MiniYaml.number(yaml, "pitch", 0));
    }

    private static void finish(CommandSourceStack source, Found found, boolean apply) {
        Lang.send(source, "admin.migrate_found", "players", found.players.size(), "homes", found.homes,
                "warps", found.warps.size(), "spawn", found.spawn != null ? "yes" : "no", "broken", found.broken);
        if (!apply) {
            Lang.send(source, "admin.migrate_dry");
            return;
        }
        int added = 0;
        int merged = 0;
        for (PlayerData imported : found.players) {
            PlayerData existing = Store.get(imported.id);
            if (existing == null) {
                Store.put(imported);
                added++;
            } else {
                imported.homes.forEach(existing.homes::putIfAbsent);
                if (existing.nick == null) {
                    existing.nick = imported.nick;
                }
                if (existing.balance == 0) {
                    existing.balance = imported.balance;
                }
                merged++;
            }
        }
        ServerData server = Store.server();
        found.warps.forEach((name, loc) -> server.warps.putIfAbsent(name, new ServerData.Warp(loc, "minecraft:ender_pearl")));
        if (found.spawn != null && server.spawn == null) {
            server.spawn = found.spawn;
        }
        Store.markDirty();
        Store.saveIfDirty();
        Lang.send(source, "admin.migrate_done", "added", added, "merged", merged, "warps", found.warps.size());
    }
}
