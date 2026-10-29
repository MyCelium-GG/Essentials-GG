package my.celium.org.essentialsgg.data;

import my.celium.org.util.PlayerUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** A saved spot: dimension id plus position and facing. Stored as is in the JSON files. */
public record Loc(String dim, double x, double y, double z, float yaw, float pitch) {

    public static Loc of(ServerPlayer player) {
        return new Loc(player.level().dimension().identifier().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    /** The level this spot is in, or {@code null} when that dimension no longer exists. */
    public ServerLevel level(MinecraftServer server) {
        Identifier id = Identifier.tryParse(dim);
        if (id == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return server.getLevel(key);
    }

    /** Teleports {@code player} here; false when the dimension is gone. */
    public boolean teleport(ServerPlayer player) {
        ServerLevel level = level(player.level().getServer());
        return level != null && PlayerUtil.teleportToLevel(player, level, x, y, z, yaw, pitch);
    }

    public long blockX() {
        return (long) Math.floor(x);
    }

    public long blockY() {
        return (long) Math.floor(y);
    }

    public long blockZ() {
        return (long) Math.floor(z);
    }

    /** {@code overworld}, {@code the_nether}, or a modded dimension's path. */
    public String dimName() {
        int colon = dim.indexOf(':');
        return colon >= 0 ? dim.substring(colon + 1) : dim;
    }
}
