package my.celium.org.essentialsgg.module;

import java.util.List;

import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;

/**
 * Portable workstations. Each is the vanilla menu tied to the player's own
 * position, with the "is the block still there" check switched off, so it
 * works anywhere and vanilla clients show their normal screens.
 * {@code /trash} is a chest whose contents vanish when closed.
 */
public final class Workbenches {
    private Workbenches() {
    }

    public static void commands() {
        station("craft", List.of("workbench", "wb"), "container.crafting",
                (id, inv, p) -> new CraftingMenu(id, inv, access(p)) {
                    @Override
                    public boolean stillValid(Player player) {
                        return true;
                    }
                });
        station("anvil", List.of(), "container.repair",
                (id, inv, p) -> new AnvilMenu(id, inv, access(p)) {
                    @Override
                    public boolean stillValid(Player player) {
                        return true;
                    }
                });
        station("grindstone", List.of(), "container.grindstone_title",
                (id, inv, p) -> new GrindstoneMenu(id, inv, access(p)) {
                    @Override
                    public boolean stillValid(Player player) {
                        return true;
                    }
                });
        station("stonecutter", List.of(), "container.stonecutter",
                (id, inv, p) -> new StonecutterMenu(id, inv, access(p)) {
                    @Override
                    public boolean stillValid(Player player) {
                        return true;
                    }
                });
        station("smithing", List.of("smithingtable"), "container.upgrade",
                (id, inv, p) -> new SmithingMenu(id, inv, access(p)) {
                    @Override
                    public boolean stillValid(Player player) {
                        return true;
                    }
                });
        station("loom", List.of(), "container.loom",
                (id, inv, p) -> new LoomMenu(id, inv, access(p)) {
                    @Override
                    public boolean stillValid(Player player) {
                        return true;
                    }
                });
        station("cartography", List.of("cartographytable"), "container.cartography_table",
                (id, inv, p) -> new CartographyTableMenu(id, inv, access(p)) {
                    @Override
                    public boolean stillValid(Player player) {
                        return true;
                    }
                });
        Cmd.define("trash", Module.WORKBENCHES, "command.trash", 0, List.of("disposal", "bin"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> {
                    p.openMenu(new SimpleMenuProvider((id, inv, player) -> new ChestMenu(MenuType.GENERIC_9x4, id, inv, new SimpleContainer(36), 4),
                            Lang.get(p, "menu.trash_title")));
                    return 1;
                }))));
    }

    private static ContainerLevelAccess access(Player player) {
        return ContainerLevelAccess.create(player.level(), player.blockPosition());
    }

    private static void station(String name, List<String> aliases, String titleKey, MenuConstructor menu) {
        Cmd.define(name, Module.WORKBENCHES, "command." + name, 2, aliases, (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> open(p, menu, titleKey)))));
    }

    private static int open(ServerPlayer player, MenuConstructor menu, String titleKey) {
        player.openMenu(new SimpleMenuProvider(menu, Component.translatable(titleKey)));
        return 1;
    }
}
