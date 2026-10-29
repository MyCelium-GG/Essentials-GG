package my.celium.org.essentialsgg.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import my.celium.org.data.MycelDataFile;
import my.celium.org.essentialsgg.EssentialsGG;
import my.celium.org.logging.MycelLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The world's Essentials GG data in two files under
 * {@code <world>/data/essentialsgg/}: {@code players.json} (every player who
 * ever joined) and {@code server.json} (spawn, warps, kits). Both are loaded
 * when the server starts and written when something changed: every minute
 * on a background thread, and once more on shutdown. Everything here runs
 * on the server thread.
 */
public final class Store {
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final int MAX_BYTES = 256 * 1024 * 1024;

    private static final Map<UUID, PlayerData> PLAYERS = new HashMap<>();
    /** Lower-case name -> id, newest owner of a name wins. */
    private static final Map<String, UUID> NAMES = new HashMap<>();
    private static ServerData server = new ServerData();
    private static MycelDataFile playersFile;
    private static MycelDataFile serverFile;
    private static boolean dirty;
    private static ExecutorService writer;

    private Store() {
    }

    public static void load(MinecraftServer mc) {
        PLAYERS.clear();
        NAMES.clear();
        playersFile = MycelDataFile.inWorld(mc, EssentialsGG.META, "players.json").withGson(COMPACT).withMaxBytes(MAX_BYTES);
        serverFile = MycelDataFile.inWorld(mc, EssentialsGG.META, "server.json").withGson(PRETTY);
        Map<UUID, PlayerData> players = playersFile.read(new TypeToken<Map<UUID, PlayerData>>() { }.getType(), HashMap::new);
        for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
            PlayerData data = entry.getValue();
            if (entry.getKey() == null || data == null) {
                continue;
            }
            data.id = entry.getKey();
            data.repair();
            PLAYERS.put(data.id, data);
            index(data);
        }
        server = serverFile.read(ServerData.class, ServerData::new);
        server.repair();
        server.warps = new TreeMap<>(server.warps);
        server.kits = new TreeMap<>(server.kits);
        server.groups = new TreeMap<>(server.groups);
        dirty = false;
        writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Essentials GG save");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Writes now on the calling thread and forgets everything (server stopping). */
    public static void close() {
        if (writer != null) {
            writer.shutdown();
            try {
                writer.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writer = null;
        }
        if (dirty && playersFile != null) {
            playersFile.write(COMPACT.toJsonTree(PLAYERS));
            serverFile.write(PRETTY.toJsonTree(server));
        }
        PLAYERS.clear();
        NAMES.clear();
        server = new ServerData();
        playersFile = null;
        serverFile = null;
        dirty = false;
    }

    /** Hands a snapshot to the background writer when something changed. */
    public static void saveIfDirty() {
        if (!dirty || writer == null) {
            return;
        }
        dirty = false;
        JsonElement players = COMPACT.toJsonTree(PLAYERS);
        JsonElement world = PRETTY.toJsonTree(server);
        MycelDataFile pf = playersFile;
        MycelDataFile sf = serverFile;
        writer.execute(() -> {
            if (!pf.write(players) | !sf.write(world)) {
                MycelLog.warnMod(EssentialsGG.ID, "Could not save Essentials GG data, retrying with the next save");
                markDirty();
            }
        });
    }

    public static synchronized void markDirty() {
        dirty = true;
    }

    public static ServerData server() {
        return server;
    }

    /** The data of an online player, created on first sight. */
    public static PlayerData of(ServerPlayer player) {
        PlayerData data = PLAYERS.get(player.getUUID());
        if (data == null) {
            data = new PlayerData(player.getUUID(), player.getGameProfile().name());
            data.firstJoin = System.currentTimeMillis();
            PLAYERS.put(data.id, data);
            index(data);
            markDirty();
        }
        return data;
    }

    /** Known data for {@code id}, or {@code null}. */
    public static PlayerData get(UUID id) {
        return PLAYERS.get(id);
    }

    /** The player who last used {@code name}, or {@code null}. */
    public static PlayerData byName(String name) {
        UUID id = NAMES.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : PLAYERS.get(id);
    }

    public static Collection<PlayerData> all() {
        return PLAYERS.values();
    }

    /** Names of every known player starting with {@code prefix}, for suggestions. */
    public static List<String> namesStartingWith(String prefix, int limit) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (PlayerData data : PLAYERS.values()) {
            if (data.name != null && data.name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(data.name);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    /** Records a (possibly changed) account name. */
    public static void rename(PlayerData data, String name) {
        if (name != null && !name.equals(data.name)) {
            data.name = name;
            markDirty();
        }
        index(data);
    }

    private static void index(PlayerData data) {
        if (data.name == null) {
            return;
        }
        String key = data.name.toLowerCase(Locale.ROOT);
        UUID current = NAMES.get(key);
        PlayerData other = current == null ? null : PLAYERS.get(current);
        if (other == null || other == data || other.lastSeen <= data.lastSeen) {
            NAMES.put(key, data.id);
        }
    }

    /** Adds or replaces imported data (migration). */
    public static void put(PlayerData data) {
        data.repair();
        PLAYERS.put(data.id, data);
        index(data);
        markDirty();
    }
}
