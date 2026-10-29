package my.celium.org.essentialsgg.module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.essentialsgg.EssentialsGG;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Args;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.data.Loc;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Teleporting, shared by every module that moves players: warmups that a
 * step cancels, {@code /back} memory, and player-to-player requests
 * ({@code /tpa}, {@code /tpahere}).
 */
public final class Teleports {
    public static ConfigValue<Integer> warmupSeconds;
    public static ConfigValue<Boolean> cancelOnMove;
    public static ConfigValue<Integer> requestSeconds;
    public static ConfigValue<Boolean> backOnDeath;

    private static final String BACK_ON_DEATH = Perms.declare(Perms.ROOT + "back.ondeath", 0);
    private static final String TPA_OTHERS_TOGGLE = Perms.declare(Perms.ROOT + "tptoggle.bypass", 2);

    /** A teleport waiting for its warmup. */
    private record Pending(Vec3 start, long due, Supplier<Loc> where, Consumer<ServerPlayer> after) {
    }

    /** A request: {@code from} asks to go to the target, or ({@code here}) asks the target to come. */
    private record Request(UUID from, boolean here, long expires) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** Target -> requester -> request, oldest first. */
    private static final Map<UUID, LinkedHashMap<UUID, Request>> REQUESTS = new HashMap<>();

    private Teleports() {
    }

    public static void config(ConfigBuilder builder) {
        builder.category("teleport");
        warmupSeconds = builder.intValue("warmupSeconds", 3, 0, 60,
                "Seconds a player stands still before /home, /warp, /spawn, /tpa and /rtp move them. "
                        + "Staff with essentialsgg.bypass.warmup go at once.");
        cancelOnMove = builder.booleanValue("cancelOnMove", true, "Moving during the warmup cancels the teleport.");
        requestSeconds = builder.intValue("requestSeconds", 120, 10, 3600, "How long a /tpa request stays open.");
        backOnDeath = builder.booleanValue("backOnDeath", true, "/back returns to where you died.");
        builder.root();
    }

    public static void commands() {
        Cmd.define("tpa", Module.TELEPORT, 0, (root, b) -> root
                .then(Args.player("player").executes(b.run(Cmd.self((p, c) -> request(p, Args.player(c, "player"), false))))));
        Cmd.define("tpahere", Module.TELEPORT, 0, (root, b) -> root
                .then(Args.player("player").executes(b.run(Cmd.self((p, c) -> request(p, Args.player(c, "player"), true))))));
        Cmd.define("tpaccept", Module.TELEPORT, "command.tpaccept", 0, List.of("tpyes"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> answer(p, null, true))))
                .then(Args.player("player").executes(b.run(Cmd.self((p, c) -> answer(p, Args.player(c, "player"), true))))));
        Cmd.define("tpdeny", Module.TELEPORT, "command.tpdeny", 0, List.of("tpno"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> answer(p, null, false))))
                .then(Args.player("player").executes(b.run(Cmd.self((p, c) -> answer(p, Args.player(c, "player"), false))))));
        Cmd.define("tpacancel", Module.TELEPORT, 0, (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> cancel(p, null))))
                .then(Args.player("player").executes(b.run(Cmd.self((p, c) -> cancel(p, Args.player(c, "player")))))));
        Cmd.define("tptoggle", Module.TELEPORT, 0, (root, b) -> root
                .executes(b.run(Cmd.self(Teleports::toggle))));
        Cmd.define("back", Module.TELEPORT, "command.back", 0, List.of("return"), (root, b) -> root
                .executes(b.run(Cmd.self(Teleports::back))));
        Cmd.define("top", Module.TELEPORT, 2, (root, b) -> root
                .executes(b.run(Cmd.self(Teleports::top))));
        Cmd.define("tphere", Module.TELEPORT, "command.tphere", 2, List.of("s"), (root, b) -> root
                .then(Args.player("player").executes(b.run(Cmd.self(Teleports::here)))));
        Cmd.define("tpall", Module.TELEPORT, 3, (root, b) -> root
                .executes(b.run(Cmd.self(Teleports::all))));
    }

    // ---------------------------------------------------------------- shared teleport

    /**
     * Moves {@code player} to {@code where} after the warmup (or at once for
     * staff and when the warmup is 0), remembering the old spot for
     * {@code /back}. {@code after} runs once they arrived.
     */
    public static void go(ServerPlayer player, Supplier<Loc> where, Consumer<ServerPlayer> after) {
        int warmup = warmupSeconds.get();
        if (warmup <= 0 || Perms.has(player, EssentialsGG.BYPASS_WARMUP)) {
            arrive(player, where, after);
            return;
        }
        PENDING.put(player.getUUID(), new Pending(player.position(), System.currentTimeMillis() + warmup * 1000L,
                where, after));
        Lang.send(player, cancelOnMove.get() ? "tp.warmup" : "tp.warmup_free", "seconds", warmup);
    }

    private static void arrive(ServerPlayer player, Supplier<Loc> where, Consumer<ServerPlayer> after) {
        Loc target = where.get();
        if (target == null) {
            Lang.send(player, "tp.target_gone");
            return;
        }
        Loc from = Loc.of(player);
        if (!target.teleport(player)) {
            Lang.send(player, "tp.world_gone", "world", target.dimName());
            return;
        }
        setBack(player, from);
        if (after != null) {
            after.accept(player);
        }
    }

    public static void setBack(ServerPlayer player, Loc where) {
        Store.of(player).back = where;
        Store.markDirty();
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty() && REQUESTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Pending pending = entry.getValue();
            if (player == null) {
                it.remove();
            } else if (cancelOnMove.get() && player.position().distanceToSqr(pending.start) > 0.25) {
                it.remove();
                Lang.send(player, "tp.cancelled_move");
            } else if (now >= pending.due) {
                it.remove();
                due.add(() -> arrive(player, pending.where, pending.after));
            }
        }
        due.forEach(Runnable::run);
        if (server.getTickCount() % 20 == 0) {
            REQUESTS.values().forEach(map -> map.values().removeIf(request -> request.expires < now));
            REQUESTS.values().removeIf(Map::isEmpty);
        }
    }

    public static void onDeath(ServerPlayer player) {
        if (Module.TELEPORT.enabled() && backOnDeath.get() && Perms.has(player, BACK_ON_DEATH)) {
            setBack(player, Loc.of(player));
        }
        PENDING.remove(player.getUUID());
    }

    public static void onLeave(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        REQUESTS.remove(player.getUUID());
        REQUESTS.values().forEach(map -> map.remove(player.getUUID()));
    }

    public static void clear() {
        PENDING.clear();
        REQUESTS.clear();
    }

    // ---------------------------------------------------------------- requests

    private static int request(ServerPlayer from, ServerPlayer to, boolean here) {
        if (from == to) {
            Lang.send(from, "tp.self");
            return 0;
        }
        PlayerData target = Store.of(to);
        if ((target.tpOff || target.ignored.contains(from.getUUID())) && !Perms.has(from, TPA_OTHERS_TOGGLE)) {
            Lang.send(from, "tp.refuses", "player", Nicknames.name(to));
            return 0;
        }
        long expires = System.currentTimeMillis() + requestSeconds.get() * 1000L;
        REQUESTS.computeIfAbsent(to.getUUID(), k -> new LinkedHashMap<>()).remove(from.getUUID());
        REQUESTS.get(to.getUUID()).put(from.getUUID(), new Request(from.getUUID(), here, expires));
        Lang.send(from, "tp.sent", "player", Nicknames.name(to), "seconds", requestSeconds.get());
        Lang.send(to, here ? "tp.asked_here" : "tp.asked", "player", Nicknames.name(from),
                "name", from.getGameProfile().name(), "seconds", requestSeconds.get());
        return 1;
    }

    private static int answer(ServerPlayer target, ServerPlayer from, boolean accept) {
        LinkedHashMap<UUID, Request> open = REQUESTS.get(target.getUUID());
        long now = System.currentTimeMillis();
        if (open != null) {
            open.values().removeIf(request -> request.expires < now);
        }
        Request request = null;
        if (open != null && !open.isEmpty()) {
            if (from != null) {
                request = open.get(from.getUUID());
            } else {
                for (Request r : open.values()) {
                    request = r;
                }
            }
        }
        if (request == null) {
            Lang.send(target, "tp.no_request");
            return 0;
        }
        open.remove(request.from);
        ServerPlayer asker = target.level().getServer().getPlayerList().getPlayer(request.from);
        if (asker == null) {
            Lang.send(target, "tp.no_request");
            return 0;
        }
        if (!accept) {
            Lang.send(target, "tp.denied", "player", Nicknames.name(asker));
            Lang.send(asker, "tp.denied_other", "player", Nicknames.name(target));
            return 1;
        }
        Lang.send(target, "tp.accepted", "player", Nicknames.name(asker));
        Lang.send(asker, "tp.accepted_other", "player", Nicknames.name(target));
        // The one who moves gets the warmup; the spot is read when it ends, so a walking target is still met.
        if (request.here) {
            go(target, () -> asker.isRemoved() ? null : Loc.of(asker), p -> Lang.send(p, "tp.arrived_player", "player", Nicknames.name(asker)));
        } else {
            go(asker, () -> target.isRemoved() ? null : Loc.of(target), p -> Lang.send(p, "tp.arrived_player", "player", Nicknames.name(target)));
        }
        return 1;
    }

    private static int cancel(ServerPlayer from, ServerPlayer to) {
        int removed = 0;
        for (Map.Entry<UUID, LinkedHashMap<UUID, Request>> entry : REQUESTS.entrySet()) {
            if ((to == null || entry.getKey().equals(to.getUUID())) && entry.getValue().remove(from.getUUID()) != null) {
                removed++;
            }
        }
        Lang.send(from, removed > 0 ? "tp.cancelled" : "tp.no_request");
        return removed;
    }

    private static int toggle(ServerPlayer player, CommandContext<CommandSourceStack> context) {
        PlayerData data = Store.of(player);
        data.tpOff = !data.tpOff;
        Store.markDirty();
        Lang.send(player, data.tpOff ? "tp.toggle_off" : "tp.toggle_on");
        return 1;
    }

    // ---------------------------------------------------------------- other commands

    private static int back(ServerPlayer player, CommandContext<CommandSourceStack> context) {
        Loc back = Store.of(player).back;
        if (back == null) {
            Lang.send(player, "tp.no_back");
            return 0;
        }
        go(player, () -> back, p -> Lang.send(p, "tp.back"));
        return 1;
    }

    private static int top(ServerPlayer player, CommandContext<CommandSourceStack> context) {
        ServerLevel level = player.level();
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y <= level.getMinY() || level.dimensionType().hasCeiling()) {
            Lang.send(player, "tp.top_none");
            return 0;
        }
        Loc from = Loc.of(player);
        new Loc(from.dim(), x + 0.5, y, z + 0.5, from.yaw(), from.pitch()).teleport(player);
        setBack(player, from);
        Lang.send(player, "tp.top");
        return 1;
    }

    private static int here(ServerPlayer player, CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = Args.player(context, "player");
        Loc from = Loc.of(target);
        Loc.of(player).teleport(target);
        setBack(target, from);
        Lang.send(player, "tp.here", "player", Nicknames.name(target));
        Lang.send(target, "tp.pulled", "player", Nicknames.name(player));
        return 1;
    }

    private static int all(ServerPlayer player, CommandContext<CommandSourceStack> context) {
        Loc to = Loc.of(player);
        int count = 0;
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other != player) {
                Loc from = Loc.of(other);
                to.teleport(other);
                setBack(other, from);
                Lang.send(other, "tp.pulled", "player", Nicknames.name(player));
                count++;
            }
        }
        Lang.send(player, "tp.all", "count", count);
        return count;
    }
}
