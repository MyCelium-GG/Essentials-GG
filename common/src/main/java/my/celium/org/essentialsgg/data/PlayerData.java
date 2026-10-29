package my.celium.org.essentialsgg.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything Essentials GG remembers about one player. Fields are public and
 * mutated in place; call {@link Store#markDirty()} after a change. Missing
 * fields in an older file come back as their defaults.
 */
public final class PlayerData {
    public UUID id;
    /** Last known account name, kept for offline lookups ({@code /seen}, bans, homes of others). */
    public String name;
    /** Formatted nickname, or {@code null}. */
    public String nick;
    /** Chosen language code, or {@code null} to follow the client's language. */
    public String lang;
    public long firstJoin;
    public long lastSeen;
    public String lastIp;
    public Map<String, Loc> homes = new LinkedHashMap<>();
    /** Where {@code /back} goes: before the last teleport, or the death spot. */
    public Loc back;
    public Loc logout;
    public double balance;
    /** Kit name -> last claim (epoch millis). */
    public Map<String, Long> kits = new LinkedHashMap<>();
    /** Refuses teleport requests. */
    public boolean tpOff;
    /** Refuses private messages. */
    public boolean msgOff;
    public boolean socialSpy;
    public boolean god;
    public boolean sidebarOff;
    public Set<UUID> ignored = new LinkedHashSet<>();
    public Mute mute;
    public List<Warning> warnings = new ArrayList<>();
    /** Permissions granted with {@code /perm}: node (wildcards allowed) -> allowed. */
    public Map<String, Boolean> perms = new LinkedHashMap<>();
    /** {@code /perm} groups this player is in. */
    public List<String> groups = new ArrayList<>();

    /** An active mute; {@code until} 0 means forever. */
    public static final class Mute {
        public long until;
        public String reason;
        public String by;

        public Mute() {
        }

        public Mute(long until, String reason, String by) {
            this.until = until;
            this.reason = reason;
            this.by = by;
        }

        public boolean expired(long now) {
            return until > 0 && now >= until;
        }
    }

    public static final class Warning {
        public long at;
        public String reason;
        public String by;

        public Warning() {
        }

        public Warning(long at, String reason, String by) {
            this.at = at;
            this.reason = reason;
            this.by = by;
        }
    }

    public PlayerData() {
    }

    public PlayerData(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    /** Fills collections an old or hand-edited file left out. */
    void repair() {
        if (homes == null) {
            homes = new LinkedHashMap<>();
        }
        if (kits == null) {
            kits = new LinkedHashMap<>();
        }
        if (ignored == null) {
            ignored = new LinkedHashSet<>();
        }
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        if (perms == null) {
            perms = new LinkedHashMap<>();
        }
        if (groups == null) {
            groups = new ArrayList<>();
        }
    }

    /** The active mute, clearing it once it ran out. */
    public Mute activeMute(long now) {
        if (mute != null && mute.expired(now)) {
            mute = null;
        }
        return mute;
    }
}
