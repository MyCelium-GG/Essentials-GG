package my.celium.org.essentialsgg.neoforge;

import java.util.HashMap;
import java.util.Map;

import my.celium.org.client.ClientHooks;
import my.celium.org.essentialsgg.EssentialsGG;
import my.celium.org.essentialsgg.module.Sidebar;
import my.celium.org.essentialsgg.platform.Perms;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.exceptions.UnregisteredPermissionException;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * NeoForge entrypoint: forwards server start, ticks, commands, chat and
 * deaths to the common code, registers every permission node with
 * NeoForge's PermissionAPI (which LuckPerms answers) and supplies tab list
 * names.
 */
@Mod(EssentialsGG.ID)
public final class EssentialsGGNeoForge {
    private static final Map<String, PermissionNode<Boolean>> NODES = new HashMap<>();
    private static final Map<String, PermissionNode<String>> META = new HashMap<>();

    public EssentialsGGNeoForge(@SuppressWarnings("unused") IEventBus modBus,
            @SuppressWarnings("unused") ModContainer container) {
        EssentialsGG.init();
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> EssentialsGG.serverStarting(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> EssentialsGG.tickServer(event.getServer()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> EssentialsGG.registerCommands(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerChatEvent event) -> {
            if (EssentialsGG.onChat(event.getPlayer(), event.getRawText())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> EssentialsGG.onDeath(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.TabListNameFormat event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                event.setDisplayName(Sidebar.tabName(player));
            }
        });
        NeoForge.EVENT_BUS.addListener(EssentialsGGNeoForge::gatherNodes);
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
        if (FMLEnvironment.getDist().isClient()) {
            ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                    () -> (ModContainer ignored, Screen parent) -> ClientHooks.createConfigScreen(parent, EssentialsGG.ID));
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
