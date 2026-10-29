package my.celium.org.essentialsgg.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;

/** World-wide data: spawn, warps, kits and permission groups. Names are stored lower case. */
public final class ServerData {
    public Loc spawn;
    /** Where brand-new players appear; falls back to {@link #spawn}. */
    public Loc firstSpawn;
    public Map<String, Warp> warps = new TreeMap<>();
    public Map<String, Kit> kits = new TreeMap<>();
    /** {@code /perm} groups; {@code default} applies to every player. */
    public Map<String, Group> groups = new TreeMap<>();

    public static final class Group {
        public Map<String, Boolean> perms = new LinkedHashMap<>();
        /** Chat and tab prefix/suffix for members, used when no permissions mod supplies one. */
        public String prefix;
        public String suffix;
        /** Higher wins when a player is in several groups (prefix, suffix, conflicting nodes). */
        public int weight;
    }

    public static final class Warp {
        public Loc loc;
        /** Item id shown in the warps menu. */
        public String icon;
        /** Only players with {@code essentialsgg.warp.<name>} may use it. */
        public boolean restricted;
        public long created;

        public Warp() {
        }

        public Warp(Loc loc, String icon) {
            this.loc = loc;
            this.icon = icon;
            this.created = System.currentTimeMillis();
        }
    }

    public static final class Kit {
        /** Each item as JSON from {@code ItemStack.CODEC}. */
        public List<JsonElement> items = new ArrayList<>();
        /** Seconds between claims; 0 is no cooldown, -1 is once ever. */
        public long cooldown;
        /** Item id shown in the kits menu. */
        public String icon;
        /** Only players with {@code essentialsgg.kit.<name>} may claim it. */
        public boolean restricted;
        /** Handed out automatically on a player's first join. */
        public boolean firstJoin;
    }

    void repair() {
        if (warps == null) {
            warps = new TreeMap<>();
        }
        if (kits == null) {
            kits = new TreeMap<>();
        }
        if (groups == null) {
            groups = new TreeMap<>();
        }
        groups.values().forEach(group -> {
            if (group.perms == null) {
                group.perms = new LinkedHashMap<>();
            }
        });
        kits.values().forEach(kit -> {
            if (kit.items == null) {
                kit.items = new ArrayList<>();
            }
        });
    }
}
