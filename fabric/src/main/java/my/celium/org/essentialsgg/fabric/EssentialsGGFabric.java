package my.celium.org.essentialsgg.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import my.celium.org.essentialsgg.EssentialsGG;
import my.celium.org.essentialsgg.module.Sidebar;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.logging.MycelLog;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Fabric entrypoint: forwards server start, ticks, commands, chat and deaths
 * to the common code. Permissions come from fabric-permissions-api
 * (LuckPerms and others) when it is installed, looked up by reflection so it
 * stays optional. Fabric has no tab-name hook, so the tab list name is sent
 * as a display-name update packet.
 */
public final class EssentialsGGFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        EssentialsGG.init();
        ServerLifecycleEvents.SERVER_STARTING.register(EssentialsGG::serverStarting);
        ServerTickEvents.END_SERVER_TICK.register(EssentialsGG::tickServer);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> EssentialsGG.registerCommands(dispatcher));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
                (message, sender, params) -> !EssentialsGG.onChat(sender, message.signedContent()));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> EssentialsGG.onDeath(entity));
        LuckPerms.install();
        Sidebar.tabNameRefresher = EssentialsGGFabric::sendTabName;
    }

    /** Sends {@code player}'s tab list name to everyone, built by {@link Sidebar#tabName}. */
    private static void sendTabName(ServerPlayer player) {
        Component name = Sidebar.tabName(player);
        ClientboundPlayerInfoUpdatePacket packet = TabPacket.of(player, name);
        if (packet != null) {
            player.level().getServer().getPlayerList().broadcastAll(packet);
        }
    }

    /** fabric-permissions-api through reflection: {@code Permissions.getPermissionValue} and {@code Options.get}. */
    private static final class LuckPerms {
        private static Method value;
        private static Method option;

        static void install() {
            try {
                Class<?> permissions = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
                value = permissions.getMethod("getPermissionValue", Entity.class, String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                return;
            }
            try {
                Class<?> options = Class.forName("me.lucko.fabric.api.permissions.v0.Options");
                option = options.getMethod("get", Entity.class, String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                option = null;
            }
            Perms.setBackend(new Perms.Backend() {
                @Override
                public Boolean check(ServerPlayer player, String node) {
                    try {
                        Object state = value.invoke(null, player, node);
                        return switch (String.valueOf(state)) {
                            case "TRUE" -> Boolean.TRUE;
                            case "FALSE" -> Boolean.FALSE;
                            default -> null;
                        };
                    } catch (ReflectiveOperationException e) {
                        return null;
                    }
                }

                @Override
                public String meta(ServerPlayer player, String key) {
                    if (option == null) {
                        return null;
                    }
                    try {
                        Object result = option.invoke(null, player, key);
                        return result instanceof Optional<?> optional && optional.isPresent()
                                ? String.valueOf(optional.get()) : null;
                    } catch (ReflectiveOperationException e) {
                        return null;
                    }
                }
            });
            MycelLog.infoMod(EssentialsGG.ID, "Using fabric-permissions-api for permissions");
        }
    }

    /** A display-name update carrying our name: vanilla always sends {@code null} on Fabric. */
    private static final class TabPacket {
        private static Field entries;
        private static boolean failed;

        static ClientboundPlayerInfoUpdatePacket of(ServerPlayer player, Component name) {
            if (failed) {
                return null;
            }
            try {
                if (entries == null) {
                    entries = ClientboundPlayerInfoUpdatePacket.class.getDeclaredField("entries");
                    entries.setAccessible(true);
                }
                ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                        EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of());
                // Only the display name is written for this action; the other fields are placeholders.
                entries.set(packet, List.of(new ClientboundPlayerInfoUpdatePacket.Entry(player.getUUID(), null, true, 0,
                        player.gameMode(), name, true, 0, null)));
                return packet;
            } catch (ReflectiveOperationException | RuntimeException e) {
                failed = true;
                MycelLog.warnMod(EssentialsGG.ID, "Tab list names unavailable on this Minecraft version: {}", e.toString());
                return null;
            }
        }
    }
}
