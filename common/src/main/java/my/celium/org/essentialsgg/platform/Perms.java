package my.celium.org.essentialsgg.platform;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import my.celium.org.essentialsgg.Module;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Permission checks. Every node is {@code essentialsgg.<something>} and has a
 * default operator level: 0 for what every player gets, 2 and up for staff.
 * The order: what was set with {@code /perm} ({@link LocalPerms}), then a
 * permissions mod (LuckPerms and friends) through the loader's
 * {@link Backend}, then the default level. The console and command blocks pass every check.
 */
public final class Perms {
    public static final String ROOT = "essentialsgg.";

    /** Answers {@code true}, {@code false}, or {@code null} for "not set, use the default". */
    @FunctionalInterface
    public interface Backend {
        Boolean check(ServerPlayer player, String node);

        /** A text option (LuckPerms meta) such as a chat prefix, or {@code null}. */
        default String meta(ServerPlayer player, String key) {
            return null;
        }
    }

    /** Text nodes loaders with typed permission APIs register as string nodes. */
    public static final List<String> META_KEYS = List.of(ROOT + "prefix", ROOT + "suffix");

    /** Every fixed node and its default level, so loaders can register them with their permission API. */
    private static final Map<String, Integer> NODES = new LinkedHashMap<>();
    private static Backend backend = (player, node) -> null;

    private Perms() {
    }

    /** Set once by the loader entrypoint. */
    public static void setBackend(Backend newBackend) {
        backend = newBackend;
    }

    /** Declares {@code node} (full name) with its default level and returns it. */
    public static String declare(String node, int defaultLevel) {
        NODES.put(node, defaultLevel);
        return node;
    }

    /** Nodes declared so far, name -> default level. */
    public static Map<String, Integer> declared() {
        return Collections.unmodifiableMap(NODES);
    }

    public static boolean has(ServerPlayer player, String node) {
        Integer level = NODES.get(node);
        return has(player, node, level == null ? 2 : level);
    }

    /** {@code node} may be undeclared (per-kit, per-warp, limit tiers); {@code defaultLevel} applies when unset. */
    public static boolean has(ServerPlayer player, String node, int defaultLevel) {
        Boolean answer = Module.PERMISSIONS.enabled() ? LocalPerms.check(player.getUUID(), node) : null;
        if (answer == null) {
            try {
                answer = backend.check(player, node);
            } catch (RuntimeException e) {
                answer = null;
            }
        }
        if (answer != null) {
            return answer;
        }
        return defaultLevel <= 0 || player.permissions().hasPermission(
                new Permission.HasCommandLevel(PermissionLevel.byId(Math.min(defaultLevel, 4))));
    }

    public static String meta(ServerPlayer player, String key) {
        if (Module.PERMISSIONS.enabled()) {
            String local = LocalPerms.meta(player.getUUID(), key.equals(ROOT + "prefix"));
            if (local != null) {
                return local;
            }
        }
        try {
            String value = backend.meta(player, key);
            return value == null || value.isEmpty() ? null : value;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean has(CommandSourceStack source, String node) {
        ServerPlayer player = source.getPlayer();
        return player == null || has(player, node);
    }

    /**
     * The highest tier in {@code tiers} the player holds as
     * {@code <node>.<tier>}, {@code Integer.MAX_VALUE} with {@code <node>.unlimited},
     * else {@code fallback}.
     */
    public static int limit(ServerPlayer player, String node, Collection<Integer> tiers, int fallback) {
        if (has(player, node + ".unlimited", 4)) {
            return Integer.MAX_VALUE;
        }
        int best = fallback;
        for (int tier : tiers) {
            if (tier > best && has(player, node + "." + tier, 99)) {
                best = tier;
            }
        }
        return best;
    }
}
