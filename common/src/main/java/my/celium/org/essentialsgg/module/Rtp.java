package my.celium.org.essentialsgg.module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.brigadier.arguments.StringArgumentType;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.essentialsgg.EssentialsGG;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.cmd.Time;
import my.celium.org.essentialsgg.data.Loc;
import my.celium.org.essentialsgg.gui.Gui;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@code /rtp}: a random safe spot within a ring around the world spawn.
 * {@code /rtp} opens a menu of the dimensions, {@code /rtp <world>} goes
 * straight there. The
 * search tries one spot per tick, so loading new chunks never stalls the
 * server for long.
 */
public final class Rtp {
    public static ConfigValue<Integer> radius;
    public static ConfigValue<Integer> minRadius;
    public static ConfigValue<Integer> cooldownSeconds;
    public static ConfigValue<Integer> attempts;
    public static ConfigValue<Boolean> overworld;
    public static ConfigValue<Boolean> nether;
    public static ConfigValue<Boolean> end;

    private static final String NETHER = Perms.declare(Perms.ROOT + "rtp.nether", 0);
    private static final String END = Perms.declare(Perms.ROOT + "rtp.end", 0);

    private static final class Search {
        final ServerLevel level;
        int left;

        Search(ServerLevel level, int left) {
            this.level = level;
            this.left = left;
        }
    }

    private static final Map<UUID, Search> SEARCHES = new HashMap<>();
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    private Rtp() {
    }

    public static void config(ConfigBuilder builder) {
        builder.category("rtp");
        radius = builder.intValue("radius", 5000, 16, 1_000_000, "Farthest distance from the world spawn (blocks).");
        minRadius = builder.intValue("minRadius", 300, 0, 1_000_000, "Closest distance from the world spawn (blocks).");
        cooldownSeconds = builder.intValue("cooldownSeconds", 300, 0, 86_400, "Seconds between two /rtp per player.");
        attempts = builder.intValue("attempts", 24, 1, 200, "Spots tried before giving up (one per tick).");
        overworld = builder.booleanValue("overworld", true, "Allow /rtp in the overworld.");
        nether = builder.booleanValue("nether", false, "Allow /rtp in the nether (needs essentialsgg.rtp.nether).");
        end = builder.booleanValue("end", false, "Allow /rtp in the end (needs essentialsgg.rtp.end).");
        builder.root();
    }

    public static void commands() {
        Cmd.define("rtp", Module.RTP, "command.rtp", 0, List.of("wild", "randomtp"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> open(p))))
                .then(Commands.argument("world", StringArgumentType.word())
                        .suggests((c, s) -> {
                            ServerPlayer p = c.getSource().getPlayer();
                            for (String world : List.of("overworld", "nether", "end")) {
                                if (p != null && allowed(p, world)) {
                                    s.suggest(world);
                                }
                            }
                            return s.buildFuture();
                        })
                        .executes(b.run(Cmd.self((p, c) -> start(p, StringArgumentType.getString(c, "world")))))));
    }

    private static boolean allowed(ServerPlayer player, String world) {
        return switch (world) {
            case "overworld" -> overworld.get();
            case "nether" -> nether.get() && Perms.has(player, NETHER);
            case "end" -> end.get() && Perms.has(player, END);
            default -> false;
        };
    }

    /** Whether the world is switched on in the config, regardless of the player. */
    private static boolean configured(String world) {
        return switch (world) {
            case "overworld" -> overworld.get();
            case "nether" -> nether.get();
            case "end" -> end.get();
            default -> false;
        };
    }

    /** Millis left on the player's /rtp cooldown, 0 when ready. */
    private static long cooldownLeft(ServerPlayer player) {
        Long last = LAST_USE.get(player.getUUID());
        if (last == null || Perms.has(player, EssentialsGG.BYPASS_COOLDOWN)) {
            return 0;
        }
        return Math.max(0, last + cooldownSeconds.get() * 1000L - System.currentTimeMillis());
    }

    /**
     * The random teleport menu: one button per dimension, showing whether it
     * is available, locked (no permission), switched off, or waiting for the
     * cooldown, plus the search radius.
     */
    private static int open(ServerPlayer player) {
        return open(player, null);
    }

    static int open(ServerPlayer player, Consumer<ServerPlayer> back) {
        long wait = cooldownLeft(player);
        Gui.Builder menu = Gui.builder(3, Lang.get(player, "menu.rtp_title"));
        String[] worlds = {"overworld", "nether", "end"};
        int[] slots = {11, 13, 15};
        for (int i = 0; i < worlds.length; i++) {
            String world = worlds[i];
            List<Component> lore = new ArrayList<>();
            lore.add(Lang.get(player, "menu.rtp_hint", "radius", radius.get()));
            Item item = icon(world);
            boolean usable = false;
            if (!configured(world)) {
                lore.add(Lang.get(player, "menu.rtp_disabled"));
                item = Items.BARRIER;
            } else if (!allowed(player, world)) {
                lore.add(Lang.get(player, "menu.rtp_locked"));
                item = Items.BARRIER;
            } else if (wait > 0) {
                lore.add(Lang.get(player, "menu.kit_wait", "time", Time.format(player, wait)));
            } else {
                lore.add(Lang.get(player, "menu.rtp_click"));
                usable = true;
            }
            menu.button(slots[i], Gui.icon(item, Lang.get(player, "menu.rtp_" + world), lore), !usable ? Gui.LOCKED : click -> {
                click.player().closeContainer();
                start(click.player(), world);
            });
        }
        menu.item(22, Gui.icon(Items.COMPASS, Lang.get(player, "menu.rtp_info"), List.of(
                Lang.get(player, "menu.rtp_info_radius", "min", minRadius.get(), "max", radius.get()),
                Lang.get(player, "menu.rtp_info_cooldown", "time",
                        cooldownSeconds.get() == 0 ? "-" : Time.format(player, cooldownSeconds.get() * 1000L)))));
        menu.back(player, back).fill().open(player);
        return 1;
    }

    private static Item icon(String world) {
        return switch (world) {
            case "nether" -> Items.NETHERRACK;
            case "end" -> Items.END_STONE;
            default -> Items.GRASS_BLOCK;
        };
    }

    private static int start(ServerPlayer player, String world) {
        if (!allowed(player, world)) {
            Lang.send(player, "rtp.disabled");
            return 0;
        }
        long wait = cooldownLeft(player);
        if (wait > 0) {
            Lang.send(player, "error.cooldown", "time", Time.format(player, wait));
            return 0;
        }
        if (SEARCHES.containsKey(player.getUUID())) {
            Lang.send(player, "rtp.searching");
            return 0;
        }
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.getLevel(switch (world) {
            case "nether" -> Level.NETHER;
            case "end" -> Level.END;
            default -> Level.OVERWORLD;
        });
        if (level == null) {
            Lang.send(player, "rtp.disabled");
            return 0;
        }
        SEARCHES.put(player.getUUID(), new Search(level, attempts.get()));
        Lang.send(player, "rtp.searching");
        return 1;
    }

    public static void tick(MinecraftServer server) {
        if (SEARCHES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Search>> it = SEARCHES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Search> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Search search = entry.getValue();
            if (player == null) {
                it.remove();
                continue;
            }
            BlockPos spot = attempt(search.level);
            if (spot != null) {
                it.remove();
                LAST_USE.put(player.getUUID(), System.currentTimeMillis());
                Loc target = new Loc(search.level.dimension().identifier().toString(), spot.getX() + 0.5, spot.getY(),
                        spot.getZ() + 0.5, player.getYRot(), 0);
                Teleports.go(player, () -> target, p -> Lang.send(p, "rtp.arrived", "x", spot.getX(),
                        "y", spot.getY(), "z", spot.getZ()));
            } else if (--search.left <= 0) {
                it.remove();
                Lang.send(player, "rtp.failed");
            }
        }
    }

    /** One random spot in the ring; its standing position when safe, else {@code null}. */
    private static BlockPos attempt(ServerLevel level) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BlockPos center = level.dimension() == Level.OVERWORLD ? level.getServer().getRespawnData().pos() : BlockPos.ZERO;
        int max = radius.get();
        int min = Math.min(minRadius.get(), max - 1);
        double angle = random.nextDouble(Math.PI * 2);
        // Uniform over the ring's area, not bunched at the centre.
        double distance = Math.sqrt(random.nextDouble((double) min * min, (double) max * max + 1));
        int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
        if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z))) {
            return null;
        }
        level.getChunk(x >> 4, z >> 4);
        if (level.dimensionType().hasCeiling()) {
            for (int y = Math.min(level.getMaxY() - 8, 120); y > level.getMinY() + 4; y--) {
                BlockPos feet = new BlockPos(x, y, z);
                if (safe(level, feet)) {
                    return feet;
                }
            }
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinY()) {
            return null;
        }
        BlockPos feet = new BlockPos(x, y, z);
        return safe(level, feet) ? feet : null;
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        BlockState ground = level.getBlockState(feet.below());
        BlockState body = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        return body.isAir() && head.isAir()
                && ground.isSolidRender()
                && ground.getFluidState().isEmpty()
                && !ground.is(Blocks.MAGMA_BLOCK) && !ground.is(Blocks.CACTUS) && !ground.is(BlockTags.FIRE)
                && !ground.is(Blocks.BEDROCK);
    }
}
