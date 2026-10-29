package my.celium.org.essentialsgg.module;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /motd} and {@code /rules}, both written in the language files
 * ({@code motd}, {@code rules}; a list of strings is one line each), the MOTD
 * on join and a welcome line for brand-new players.
 */
public final class Info {
    public static ConfigValue<Boolean> motdOnJoin;
    public static ConfigValue<Boolean> welcomeNewPlayers;

    private Info() {
    }

    public static void config(ConfigBuilder builder) {
        builder.category("info");
        motdOnJoin = builder.booleanValue("motdOnJoin", true, "Show the MOTD (language key 'motd') when a player joins.");
        welcomeNewPlayers = builder.booleanValue("welcomeNewPlayers", true,
                "Tell everyone when someone joins for the first time (language key 'info.welcome').");
        builder.root();
    }

    public static void commands() {
        Cmd.define("motd", Module.INFO, 0, (root, b) -> root
                .executes(b.run(c -> {
                    ServerPlayer player = c.getSource().getPlayer();
                    if (player != null) {
                        motd(player);
                    } else {
                        Lang.send(c.getSource(), "motd", "player", "Console", "online", online(c.getSource().getServer()),
                                "max", c.getSource().getServer().getMaxPlayers());
                    }
                    return 1;
                })));
        Cmd.define("rules", Module.INFO, 0, (root, b) -> root
                .executes(b.run(c -> {
                    Lang.send(c.getSource(), "rules");
                    return 1;
                })));
    }

    private static int online(MinecraftServer server) {
        return server.getPlayerList().getPlayerCount();
    }

    private static void motd(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Lang.send(player, "motd", "player", Nicknames.name(player), "online", online(server), "max", server.getMaxPlayers());
    }

    public static void onJoin(ServerPlayer player) {
        if (Module.INFO.enabled() && motdOnJoin.get()) {
            motd(player);
        }
    }

    public static void onFirstJoin(ServerPlayer player) {
        if (Module.INFO.enabled() && welcomeNewPlayers.get()) {
            Lang.broadcast(player.level().getServer(), "info.welcome", "player", Nicknames.name(player),
                    "count", Store.all().size());
        }
    }
}
