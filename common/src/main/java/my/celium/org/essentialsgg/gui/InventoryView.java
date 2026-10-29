package my.celium.org.essentialsgg.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * Another player's inventory (or any container) in a chest menu, live:
 * changes on either side show at once. Read-only unless {@code editable};
 * for a player inventory the layout is main inventory, hotbar, then armor
 * and off-hand in the bottom row.
 */
public final class InventoryView {
    private InventoryView() {
    }

    /** Opens {@code target}'s inventory for {@code viewer}. */
    public static void open(ServerPlayer viewer, ServerPlayer target, Component title, boolean editable) {
        PlayerSlots slots = new PlayerSlots(target);
        viewer.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                new View(MenuType.GENERIC_9x5, id, inventory, slots, 5, editable, 41), title));
    }

    /** Opens a 27-slot container (an ender chest) for {@code viewer}. */
    public static void open(ServerPlayer viewer, Container container, Component title, boolean editable) {
        viewer.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                new View(MenuType.GENERIC_9x3, id, inventory, container, 3, editable, 27), title));
    }

    private static final class View extends ChestMenu {
        private final boolean editable;
        /** Container slots past this index are padding and never take items. */
        private final int usable;
        private final int size;

        View(MenuType<?> type, int id, Inventory inventory, Container container, int rows, boolean editable, int usable) {
            super(type, id, inventory, container, rows);
            this.editable = editable;
            this.usable = usable;
            this.size = rows * 9;
        }

        @Override
        public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
            boolean padding = slotIndex >= usable && slotIndex < size;
            // Shift-clicks from the viewer's own inventory could land in padding slots: only allow plain moves there.
            boolean risky = input == ContainerInput.QUICK_MOVE || input == ContainerInput.QUICK_CRAFT
                    || input == ContainerInput.PICKUP_ALL;
            if (editable && !padding && !(risky && usable < size)) {
                super.clicked(slotIndex, buttonNum, input, player);
                return;
            }
            // Refused: undo what the client already showed.
            sendAllDataToRemote();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slotIndex) {
            return editable && usable >= size ? super.quickMoveStack(player, slotIndex) : ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return getContainer().stillValid(player);
        }
    }

    /** 45 slots over a player's 41: main (9-35), hotbar (0-8), armor (36-39), off-hand (40), then 4 padding. */
    private static final class PlayerSlots implements Container {
        private final ServerPlayer target;
        private final Inventory inventory;

        PlayerSlots(ServerPlayer target) {
            this.target = target;
            this.inventory = target.getInventory();
        }

        private int map(int slot) {
            if (slot < 27) {
                return slot + 9;
            }
            if (slot < 36) {
                return slot - 27;
            }
            return slot < 41 ? slot : -1;
        }

        @Override
        public int getContainerSize() {
            return 45;
        }

        @Override
        public boolean isEmpty() {
            return inventory.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            int real = map(slot);
            return real < 0 ? ItemStack.EMPTY : inventory.getItem(real);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            int real = map(slot);
            return real < 0 ? ItemStack.EMPTY : inventory.removeItem(real, count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            int real = map(slot);
            return real < 0 ? ItemStack.EMPTY : inventory.removeItemNoUpdate(real);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            int real = map(slot);
            if (real >= 0) {
                inventory.setItem(real, stack);
            }
        }

        @Override
        public void setChanged() {
            inventory.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return !target.isRemoved() && !target.hasDisconnected();
        }

        @Override
        public void clearContent() {
            // Never wipe someone's inventory from a viewer.
        }
    }
}
