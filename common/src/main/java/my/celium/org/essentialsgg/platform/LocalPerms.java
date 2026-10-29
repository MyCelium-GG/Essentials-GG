package my.celium.org.essentialsgg.platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.ServerData;
import my.celium.org.essentialsgg.data.Store;

/**
 * The built-in permissions set with {@code /perm}: nodes granted (or denied)
 * per player and per group, with wildcards ({@code essentialsgg.kit.*},
 * {@code *}). The group {@code default} applies to everyone.
 *
 * <p>A player's own nodes win over their groups. Within one side the most
 * specific match wins (an exact node beats {@code essentialsgg.kit.*}, which
 * beats {@code essentialsgg.*}); between groups at the same specificity the
 * higher weight wins.
 */
public final class LocalPerms {
    public static final String DEFAULT_GROUP = "default";

    private LocalPerms() {
    }

    /** {@code true}/{@code false} when a {@code /perm} entry decides, else {@code null}. */
    public static Boolean check(UUID player, String node) {
        PlayerData data = Store.get(player);
        if (data != null) {
            Match own = best(data.perms, node, 0);
            if (own != null) {
                return own.allowed;
            }
        }
        Match best = null;
        for (ServerData.Group group : groupsOf(data)) {
            Match match = best(group.perms, node, group.weight);
            if (match != null && (best == null || match.compareTo(best) > 0)) {
                best = match;
            }
        }
        return best == null ? null : best.allowed;
    }

    /** The prefix or suffix of the highest-weight group that has one, or {@code null}. */
    public static String meta(UUID player, boolean prefix) {
        for (ServerData.Group group : groupsOf(Store.get(player))) {
            String value = prefix ? group.prefix : group.suffix;
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /** The player's groups plus {@code default}, highest weight first. */
    static List<ServerData.Group> groupsOf(PlayerData data) {
        Map<String, ServerData.Group> all = Store.server().groups;
        List<ServerData.Group> out = new ArrayList<>();
        if (data != null) {
            for (String name : data.groups) {
                ServerData.Group group = all.get(name);
                if (group != null) {
                    out.add(group);
                }
            }
        }
        ServerData.Group fallback = all.get(DEFAULT_GROUP);
        if (fallback != null && !out.contains(fallback)) {
            out.add(fallback);
        }
        out.sort(Comparator.comparingInt((ServerData.Group group) -> group.weight).reversed());
        return out;
    }

    /** One matching entry: how specific it is, the group's weight, and its value. */
    private record Match(int specificity, int weight, boolean allowed) implements Comparable<Match> {
        @Override
        public int compareTo(Match other) {
            if (specificity != other.specificity) {
                return Integer.compare(specificity, other.specificity);
            }
            return Integer.compare(weight, other.weight);
        }
    }

    private static Match best(Map<String, Boolean> entries, String node, int weight) {
        Match best = null;
        for (Map.Entry<String, Boolean> entry : entries.entrySet()) {
            int specificity = specificity(entry.getKey(), node);
            if (specificity >= 0 && (best == null || specificity > best.specificity)) {
                best = new Match(specificity, weight, Boolean.TRUE.equals(entry.getValue()));
            }
        }
        return best;
    }

    /** How closely {@code pattern} matches {@code node}: higher is more specific, -1 is no match. */
    static int specificity(String pattern, String node) {
        if (pattern.equals(node)) {
            return Integer.MAX_VALUE;
        }
        if (pattern.equals("*")) {
            return 0;
        }
        if (pattern.endsWith(".*")) {
            String base = pattern.substring(0, pattern.length() - 1);
            return node.startsWith(base) ? base.length() : -1;
        }
        return -1;
    }
}
