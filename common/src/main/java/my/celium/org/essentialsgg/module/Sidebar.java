package my.celium.org.essentialsgg.module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.cmd.Time;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.text.Fmt;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * The per-player sidebar and the tab list. Both are drawn with packets only,
 * so the world's real scoreboard is never touched and vanilla clients need
 * nothing. Texts live in the language files ({@code sidebar.title},
 * {@code sidebar.lines}, {@code tab.header}, {@code tab.footer},
 * {@code tab.name}) and refresh every second.
 */
public final class Sidebar {
    public static ConfigValue<Integer> refreshSeconds;

    private static final String OBJECTIVE = "essentialsgg";
    private static final Scoreboard DETACHED = new Scoreboard();
    /** Lines shown per player, to clear the ones a shorter sidebar no longer uses. */
    private static final Map<UUID, Integer> SHOWN = new HashMap<>();

    /** Asks the loader to resend a player's tab list name; set by each loader entrypoint. */
    public static Consumer<ServerPlayer> tabNameRefresher = player -> { };

    private Sidebar() {
    }

    public static void config(ConfigBuilder builder) {
        builder.category("sidebar");
        refreshSeconds = builder.intValue("refreshSeconds", 1, 1, 60, "How often the sidebar and tab list update.");
        builder.root();
    }

    public static void commands() {
        Cmd.define("sidebar", Module.SIDEBAR, 0, (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> {
                    PlayerData data = Store.of(p);
                    data.sidebarOff = !data.sidebarOff;
                    Store.markDirty();
                    if (data.sidebarOff) {
                        hide(p);
                    } else {
                        show(p);
                    }
                    Lang.send(p, data.sidebarOff ? "sidebar.off" : "sidebar.on");
                    return 1;
                }))));
    }

    // ---------------------------------------------------------------- placeholders

    /** Values every sidebar and tab text may use. */
    private static Map<String, Component> values(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        PlayerData data = Store.of(player);
        Map<String, Component> values = new HashMap<>();
        values.put("name", Nicknames.name(player));
        values.put("player", Component.literal(player.getGameProfile().name()));
        values.put("online", Component.literal(String.valueOf(server.getPlayerList().getPlayerCount())));
        values.put("max", Component.literal(String.valueOf(server.getMaxPlayers())));
        values.put("world", Component.literal(player.level().dimension().identifier().getPath()));
        values.put("x", Component.literal(String.valueOf(player.getBlockX())));
        values.put("y", Component.literal(String.valueOf(player.getBlockY())));
        values.put("z", Component.literal(String.valueOf(player.getBlockZ())));
        values.put("ping", Component.literal(String.valueOf(player.connection.latency())));
        values.put("balance", Component.literal(Module.ECONOMY.enabled() ? Economy.format(data.balance) : "-"));
        values.put("homes", Component.literal(String.valueOf(data.homes.size())));
        values.put("kills", Component.literal(String.valueOf(player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS)))));
        values.put("deaths", Component.literal(String.valueOf(player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)))));
        long ticks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        values.put("playtime", Component.literal(Time.format(player, ticks * 50)));
        values.put("afk", Afk.isAfk(player) ? Lang.get(player, "tab.afk") : Component.empty());
        values.put("group_prefix", Fmt.parse(Chat.meta(player, Chat.META_PREFIX)));
        values.put("group_suffix", Fmt.parse(Chat.meta(player, Chat.META_SUFFIX)));
        return values;
    }

    private static Component text(ServerPlayer player, String key, Map<String, Component> values) {
        return Fmt.parse(Lang.raw(player, key), values);
    }

    // ---------------------------------------------------------------- tab list

    /** The tab list name for {@code player}, or {@code null} to leave it to vanilla. */
    public static Component tabName(ServerPlayer player) {
        if (!Module.TABLIST.enabled() || Store.get(player.getUUID()) == null) {
            return null;
        }
        Map<String, Component> values = new HashMap<>();
        values.put("name", Nicknames.name(player));
        values.put("player", Component.literal(player.getGameProfile().name()));
        values.put("afk", Afk.isAfk(player) ? Lang.get(Lang.DEFAULT, "tab.afk") : Component.empty());
        values.put("group_prefix", Fmt.parse(Chat.meta(player, Chat.META_PREFIX)));
        values.put("group_suffix", Fmt.parse(Chat.meta(player, Chat.META_SUFFIX)));
        return Fmt.parse(Lang.raw(Lang.DEFAULT, "tab.name"), values);
    }

    public static void updateTabName(ServerPlayer player) {
        if (Module.TABLIST.enabled()) {
            tabNameRefresher.accept(player);
        }
    }

    private static void tabHeader(ServerPlayer player, Map<String, Component> values) {
        player.connection.send(new ClientboundTabListPacket(text(player, "tab.header", values), text(player, "tab.footer", values)));
    }

    // ---------------------------------------------------------------- sidebar

    private static Objective objective(Component title) {
        return new Objective(DETACHED, OBJECTIVE, ObjectiveCriteria.DUMMY, title, ObjectiveCriteria.RenderType.INTEGER,
                false, BlankFormat.INSTANCE);
    }

    private static boolean wanted(ServerPlayer player) {
        PlayerData data = Store.get(player.getUUID());
        return Module.SIDEBAR.enabled() && data != null && !data.sidebarOff;
    }

    private static void show(ServerPlayer player) {
        if (!wanted(player)) {
            return;
        }
        Map<String, Component> values = values(player);
        Objective objective = objective(text(player, "sidebar.title", values));
        boolean fresh = !SHOWN.containsKey(player.getUUID());
        player.connection.send(new ClientboundSetObjectivePacket(objective,
                fresh ? ClientboundSetObjectivePacket.METHOD_ADD : ClientboundSetObjectivePacket.METHOD_CHANGE));
        if (fresh) {
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
        }
        List<Component> lines = new ArrayList<>();
        for (String line : Lang.raw(player, "sidebar.lines").split("<newline>", -1)) {
            lines.add(Fmt.parse(line, values));
        }
        int count = Math.min(15, lines.size());
        for (int i = 0; i < count; i++) {
            // Scores count down so the first line is on top; owners are unique and invisible.
            player.connection.send(new ClientboundSetScorePacket("line" + i, OBJECTIVE, count - i,
                    Optional.of(lines.get(i)), Optional.of(BlankFormat.INSTANCE)));
        }
        int before = SHOWN.getOrDefault(player.getUUID(), 0);
        for (int i = count; i < before; i++) {
            player.connection.send(new ClientboundResetScorePacket("line" + i, OBJECTIVE));
        }
        SHOWN.put(player.getUUID(), count);
    }

    private static void hide(ServerPlayer player) {
        if (SHOWN.remove(player.getUUID()) != null) {
            player.connection.send(new ClientboundSetObjectivePacket(objective(Component.empty()),
                    ClientboundSetObjectivePacket.METHOD_REMOVE));
        }
    }

    // ---------------------------------------------------------------- lifecycle

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % (20L * refreshSeconds.get()) != 0) {
            return;
        }
        boolean sidebar = Module.SIDEBAR.enabled();
        boolean tab = Module.TABLIST.enabled();
        if (!sidebar && !tab) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (sidebar) {
                show(player);
            }
            if (tab) {
                tabHeader(player, values(player));
            }
        }
    }

    public static void onJoin(ServerPlayer player) {
        if (Module.TABLIST.enabled()) {
            // The joining player's name for everyone, everyone's names for the joining player.
            for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
                tabNameRefresher.accept(other);
            }
        }
    }

    public static void onLeave(ServerPlayer player) {
        SHOWN.remove(player.getUUID());
    }

    public static void clear() {
        SHOWN.clear();
    }
}
