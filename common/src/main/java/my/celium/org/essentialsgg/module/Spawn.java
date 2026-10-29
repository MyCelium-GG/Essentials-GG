package my.celium.org.essentialsgg.module;

import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Args;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.data.Loc;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

/**
 * {@code /spawn} and {@code /setspawn}. Setting the spawn also moves the
 * vanilla world spawn, so compasses and bed-less respawns agree. Without a
 * set spawn, {@code /spawn} uses the world spawn.
 */
public final class Spawn {
    public static ConfigValue<Boolean> newPlayersAtSpawn;
    public static ConfigValue<Boolean> everyJoinAtSpawn;

    private static final String OTHERS = Perms.declare(Perms.ROOT + "spawn.others", 2);

    private Spawn() {
    }

    public static void config(ConfigBuilder builder) {
        builder.category("spawn");
        newPlayersAtSpawn = builder.booleanValue("newPlayersAtSpawn", true,
                "Players joining for the first time start at the spawn (or the /setspawn firstjoin spot).");
        everyJoinAtSpawn = builder.booleanValue("everyJoinAtSpawn", false, "Every join starts at the spawn.");
        builder.root();
    }

    public static void commands() {
        Cmd.define("spawn", Module.SPAWN, 0, (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> spawn(p))))
                .then(Args.player("player").requires(s -> Perms.has(s, OTHERS))
                        .executes(b.run(c -> other(c.getSource(), Args.player(c, "player"))))));
        Cmd.define("setspawn", Module.SPAWN, "command.setspawn", 3, List.of(), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> set(p, false))))
                .then(Commands.literal("firstjoin").executes(b.run(Cmd.self((p, c) -> set(p, true))))));
    }

    /** The spawn spot: the one set with {@code /setspawn}, else the world spawn. */
    public static Loc spawn(MinecraftServer server) {
        Loc set = Store.server().spawn;
        if (set != null) {
            return set;
        }
        LevelData.RespawnData data = server.getRespawnData();
        ServerLevel level = server.getLevel(data.dimension());
        if (level == null) {
            level = server.overworld();
        }
        BlockPos pos = data.pos();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return new Loc(level.dimension().identifier().toString(), pos.getX() + 0.5, Math.max(y, pos.getY()),
                pos.getZ() + 0.5, data.yaw(), data.pitch());
    }

    private static int spawn(ServerPlayer player) {
        Teleports.go(player, () -> spawn(player.level().getServer()), p -> Lang.send(p, "spawn.arrived"));
        return 1;
    }

    private static int other(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        Loc from = Loc.of(target);
        if (spawn(source.getServer()).teleport(target)) {
            Teleports.setBack(target, from);
        }
        Lang.send(source, "spawn.sent", "player", Nicknames.name(target));
        Lang.send(target, "spawn.arrived");
        return 1;
    }

    private static int set(ServerPlayer player, boolean firstJoin) {
        Loc here = Loc.of(player);
        if (firstJoin) {
            Store.server().firstSpawn = here;
        } else {
            Store.server().spawn = here;
            player.level().getServer().setRespawnData(LevelData.RespawnData.of(player.level().dimension(),
                    player.blockPosition(), player.getYRot(), player.getXRot()));
        }
        Store.markDirty();
        Lang.send(player, firstJoin ? "spawn.set_first" : "spawn.set", "x", here.blockX(), "y", here.blockY(),
                "z", here.blockZ());
        return 1;
    }

    public static void onFirstJoin(ServerPlayer player) {
        if (!Module.SPAWN.enabled() || !newPlayersAtSpawn.get()) {
            return;
        }
        Loc first = Store.server().firstSpawn;
        (first != null ? first : spawn(player.level().getServer())).teleport(player);
    }

    /** Every-join spawn; first joins are handled by {@link #onFirstJoin}. */
    public static void onJoin(ServerPlayer player, boolean first) {
        if (Module.SPAWN.enabled() && everyJoinAtSpawn.get() && !first) {
            spawn(player.level().getServer()).teleport(player);
        }
    }
}
