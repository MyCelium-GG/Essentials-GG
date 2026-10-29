package my.celium.org.essentialsgg.module;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * AFK: {@code /afk}, or automatically after a while without moving, looking
 * around or chatting. Others see it in chat and in the tab list; an optional
 * kick frees the slot.
 */
public final class Afk {
    public static ConfigValue<Integer> autoSeconds;
    public static ConfigValue<Integer> kickSeconds;
    public static ConfigValue<Boolean> announce;

    private static final String KICK_EXEMPT = Perms.declare(Perms.ROOT + "afk.kickexempt", 2);
    private static final String AUTO_EXEMPT = Perms.declare(Perms.ROOT + "afk.autoexempt", 99);

    /** Last activity per player (millis) and the look/position seen then. */
    private static final Map<UUID, Long> ACTIVE = new HashMap<>();
    private static final Map<UUID, double[]> SEEN = new HashMap<>();
    private static final Set<UUID> AFK = new HashSet<>();

    private Afk() {
    }

    public static void config(ConfigBuilder builder) {
        builder.category("afk");
        autoSeconds = builder.intValue("autoSeconds", 300, 0, 86_400, "Seconds idle before a player is AFK; 0 turns automatic AFK off.");
        kickSeconds = builder.intValue("kickSeconds", 0, 0, 86_400, "Seconds idle before a kick; 0 never kicks.");
        announce = builder.booleanValue("announce", true, "Tell everyone when a player goes AFK or comes back.");
        builder.root();
    }

    public static void commands() {
        Cmd.define("afk", Module.AFK, 0, (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> {
                    set(p, !AFK.contains(p.getUUID()));
                    if (AFK.contains(p.getUUID())) {
                        // Standing still right after /afk must not count as coming back.
                        SEEN.put(p.getUUID(), look(p));
                    }
                    return 1;
                }))));
    }

    public static boolean isAfk(ServerPlayer player) {
        return AFK.contains(player.getUUID());
    }

    /** Something the player did on purpose: chat, a command, moving. */
    public static void activity(ServerPlayer player) {
        ACTIVE.put(player.getUUID(), System.currentTimeMillis());
        if (AFK.contains(player.getUUID())) {
            set(player, false);
        }
    }

    private static void set(ServerPlayer player, boolean afk) {
        boolean changed = afk ? AFK.add(player.getUUID()) : AFK.remove(player.getUUID());
        if (!afk) {
            ACTIVE.put(player.getUUID(), System.currentTimeMillis());
        }
        if (!changed) {
            return;
        }
        if (announce.get()) {
            Lang.broadcast(player.level().getServer(), afk ? "afk.now" : "afk.back", "player", Nicknames.name(player));
        } else {
            Lang.send(player, afk ? "afk.now_self" : "afk.back_self");
        }
        Sidebar.updateTabName(player);
    }

    private static double[] look(ServerPlayer player) {
        return new double[] {player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()};
    }

    public static void tick(MinecraftServer server) {
        if (!Module.AFK.enabled() || server.getTickCount() % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            double[] look = look(player);
            double[] before = SEEN.put(id, look);
            // Turning the head counts; being pushed by water or a piston alone does not.
            boolean turned = before != null && (Math.abs(look[3] - before[3]) > 0.5 || Math.abs(look[4] - before[4]) > 0.5);
            boolean walked = before != null && Math.abs(look[0] - before[0]) + Math.abs(look[2] - before[2]) > 0.2
                    && player.onGround();
            if (turned || walked) {
                activity(player);
                continue;
            }
            long idle = now - ACTIVE.getOrDefault(id, now);
            int auto = autoSeconds.get();
            if (auto > 0 && idle >= auto * 1000L && !AFK.contains(id) && !Perms.has(player, AUTO_EXEMPT)) {
                set(player, true);
            }
            int kick = kickSeconds.get();
            if (kick > 0 && idle >= kick * 1000L && !Perms.has(player, KICK_EXEMPT)) {
                player.connection.disconnect(Lang.get(player, "afk.kicked"));
            }
        }
    }

    public static void onJoin(ServerPlayer player) {
        ACTIVE.put(player.getUUID(), System.currentTimeMillis());
        SEEN.remove(player.getUUID());
    }

    public static void onLeave(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        SEEN.remove(player.getUUID());
        AFK.remove(player.getUUID());
    }
}
