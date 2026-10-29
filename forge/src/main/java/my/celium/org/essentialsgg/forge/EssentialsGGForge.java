package my.celium.org.essentialsgg.forge;

import java.util.HashMap;
import java.util.Map;

import my.celium.org.client.ClientHooks;
import my.celium.org.essentialsgg.EssentialsGG;
import my.celium.org.essentialsgg.module.Sidebar;
import my.celium.org.essentialsgg.platform.Perms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.exceptions.UnregisteredPermissionException;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

/**
 * Forge entrypoint: forwards server start, ticks, commands, chat and deaths
 * to the common code, registers every permission node with Forge's
 * PermissionAPI (which LuckPerms answers) and supplies tab list names.
 */
@Mod(EssentialsGG.ID)
public final class EssentialsGGForge {
    private static final Map<String, PermissionNode<Boolean>> NODES = new HashMap<>();
    private static final Map<String, PermissionNode<String>> META = new HashMap<>();

    public EssentialsGGForge(@SuppressWarnings("unused") FMLJavaModLoadingContext context) {
        EssentialsGG.init();
        ServerAboutToStartEvent.BUS.addListener(event -> EssentialsGG.serverStarting(event.getServer()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> EssentialsGG.tickServer(event.server()));
        RegisterCommandsEvent.BUS.addListener(event -> EssentialsGG.registerCommands(event.getDispatcher()));
        ServerChatEvent.BUS.addListener((ServerChatEvent event) -> EssentialsGG.onChat(event.getPlayer(), event.getRawText()));
        LivingDeathEvent.BUS.addListener((LivingDeathEvent event) -> EssentialsGG.onDeath(event.getEntity()));
        PlayerEvent.TabListNameFormat.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                event.setDisplayName(Sidebar.tabName(player));
            }
        });
        PermissionGatherEvent.Nodes.BUS.addListener(EssentialsGGForge::gatherNodes);
        Perms.setBackend(new Perms.Backend() {
            @Override
            public Boolean check(ServerPlayer player, String node) {
                PermissionNode<Boolean> registered = NODES.get(node);
                if (registered == null) {
                    return null;
                }
                try {
                    return PermissionAPI.getPermission(player, registered);
                } catch (UnregisteredPermissionException e) {
                    return null;
                }
            }

            @Override
            public String meta(ServerPlayer player, String key) {
                PermissionNode<String> registered = META.get(key);
                if (registered == null) {
                    return null;
                }
                try {
                    return PermissionAPI.getPermission(player, registered);
                } catch (UnregisteredPermissionException e) {
                    return null;
                }
            }
        });
        Sidebar.tabNameRefresher = ServerPlayer::refreshTabListName;
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (Minecraft minecraft, Screen parent) -> ClientHooks.createConfigScreen(parent, EssentialsGG.ID)));
        }
    }

    /** Every declared node, defaulting to its operator level when the permissions mod has no say. */
    private static void gatherNodes(PermissionGatherEvent.Nodes event) {
        NODES.clear();
        META.clear();
        for (Map.Entry<String, Integer> entry : EssentialsGG.permissionNodes()) {
            String name = entry.getKey().substring(Perms.ROOT.length());
            int level = entry.getValue();
            PermissionNode<Boolean> node = new PermissionNode<>(EssentialsGG.ID, name, PermissionTypes.BOOLEAN,
                    (player, uuid, context) -> player != null && (level <= 0 || player.permissions().hasPermission(
                            new Permission.HasCommandLevel(PermissionLevel.byId(Math.min(level, 4))))));
            NODES.put(entry.getKey(), node);
            event.addNodes(node);
        }
        for (String key : Perms.META_KEYS) {
            PermissionNode<String> node = new PermissionNode<>(EssentialsGG.ID, key.substring(Perms.ROOT.length()),
                    PermissionTypes.STRING, (player, uuid, context) -> "");
            META.put(key, node);
            event.addNodes(node);
        }
    }
}
