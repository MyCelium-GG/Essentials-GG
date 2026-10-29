package my.celium.org.essentialsgg;

import my.celium.org.essentialsgg.data.Loc;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.gui.Prompt;
import my.celium.org.essentialsgg.module.Afk;
import my.celium.org.essentialsgg.module.Discord;
import my.celium.org.essentialsgg.module.Economy;
import my.celium.org.essentialsgg.module.Info;
import my.celium.org.essentialsgg.module.Kits;
import my.celium.org.essentialsgg.module.Messaging;
import my.celium.org.essentialsgg.module.Sidebar;
import my.celium.org.essentialsgg.module.Spawn;
import my.celium.org.essentialsgg.module.Teleports;
import net.minecraft.server.level.ServerPlayer;

/** What happens when a player joins or leaves, across modules. */
final class Players {
    private Players() {
    }

    static void join(ServerPlayer player) {
        PlayerData data = Store.of(player);
        boolean first = data.lastSeen == 0;
        data.lastSeen = System.currentTimeMillis();
        data.lastIp = player.getIpAddress();
        Store.rename(data, player.getGameProfile().name());
        Store.markDirty();

        if (first) {
            Economy.onFirstJoin(data);
            Spawn.onFirstJoin(player);
            Kits.onFirstJoin(player);
            Info.onFirstJoin(player);
        }
        Spawn.onJoin(player, first);
        Info.onJoin(player);
        Afk.onJoin(player);
        Sidebar.onJoin(player);
        Discord.onJoin(player);
    }

    static void leave(ServerPlayer player) {
        PlayerData data = Store.get(player.getUUID());
        if (data != null) {
            data.lastSeen = System.currentTimeMillis();
            data.logout = Loc.of(player);
            Store.markDirty();
        }
        Teleports.onLeave(player);
        Messaging.onLeave(player);
        Afk.onLeave(player);
        Prompt.forget(player);
        Sidebar.onLeave(player);
        Discord.onLeave(player);
    }
}
