package my.celium.org.essentialsgg.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.gui.Gui;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * {@code /menu}: one menu leading to the homes, warps, kits and random
 * teleport menus, showing only what the player could reach by command.
 */
public final class Hub {
    private record Link(String command, Item icon, String name, String hint, Consumer<ServerPlayer> open) {
    }

    private Hub() {
    }

    public static void commands() {
        Cmd.define("menu", Module.CORE, "command.menu", 0, List.of("gui"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> open(p)))));
    }

    private static int open(ServerPlayer player) {
        List<Link> links = new ArrayList<>();
        for (Link link : List.of(
                new Link("homes", Gui.item("minecraft:red_bed", Items.COMPASS), "menu.hub_homes", "menu.hub_homes_hint", p -> Homes.openOwn(p, Hub::open)),
                new Link("warp", Items.ENDER_PEARL, "menu.hub_warps", "menu.hub_warps_hint", p -> Warps.menu(p, Hub::open)),
                new Link("kit", Items.CHEST, "menu.hub_kits", "menu.hub_kits_hint", p -> Kits.menu(p, Hub::open)),
                new Link("rtp", Items.COMPASS, "menu.hub_rtp", "menu.hub_rtp_hint", p -> Rtp.open(p, Hub::open)))) {
            if (Cmd.usable(player, link.command)) {
                links.add(link);
            }
        }
        if (links.isEmpty()) {
            Lang.send(player, "menu.hub_empty");
            return 0;
        }
        Gui.Builder menu = Gui.builder(3, Lang.get(player, "menu.hub_title"));
        int[] columns = Gui.centered(links.size());
        for (int i = 0; i < links.size(); i++) {
            Link link = links.get(i);
            menu.button(9 + columns[i], Gui.icon(link.icon, Lang.get(player, link.name), List.of(Lang.get(player, link.hint))),
                    click -> link.open.accept(click.player()));
        }
        menu.fill().open(player);
        return 1;
    }
}
