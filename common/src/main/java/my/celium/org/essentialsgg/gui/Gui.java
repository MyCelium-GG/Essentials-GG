package my.celium.org.essentialsgg.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * A clickable chest menu made of plain vanilla parts, so vanilla clients open
 * it like any chest. Nothing can be taken out or put in: every click is
 * answered by re-sending the menu, and a button runs its action on the
 * server thread.
 *
 * <pre>{@code
 * Gui.builder(3, title)
 *     .button(13, Gui.icon(Items.ENDER_PEARL, name, lore), click -> Homes.go(click.player(), home))
 *     .open(player);
 * }</pre>
 */
public final class Gui extends ChestMenu {
    private final Map<Integer, Consumer<Click>> actions;
    private final int size;

    /** A click on a button: who, and whether it was a right or shift click. */
    public record Click(ServerPlayer player, boolean right, boolean shift) {
    }

    private Gui(MenuType<?> type, int containerId, Inventory inventory, SimpleContainer container, int rows,
            Map<Integer, Consumer<Click>> actions) {
        super(type, containerId, inventory, container, rows);
        this.actions = actions;
        this.size = rows * 9;
    }

    public static Builder builder(int rows, Component title) {
        return new Builder(Math.max(1, Math.min(6, rows)), title);
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        // The client already moved the icon it thinks it picked up (onto the cursor, or into the
        // inventory on a shift-click): undo that first, before the action may close this menu.
        setCarried(ItemStack.EMPTY);
        sendAllDataToRemote();
        if (slotIndex >= 0 && slotIndex < size && player instanceof ServerPlayer serverPlayer
                && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
            Consumer<Click> action = actions.get(slotIndex);
            if (action != null) {
                action.accept(new Click(serverPlayer, buttonNum == 1, input == ContainerInput.QUICK_MOVE));
            }
        }
        // Closed by the action: the inventory screen takes over, so resend the real inventory too.
        if (player.containerMenu != this) {
            player.inventoryMenu.sendAllDataToRemote();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static MenuType<ChestMenu> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    public static final class Builder {
        private final int rows;
        private final Component title;
        private final SimpleContainer container;
        private final Map<Integer, Consumer<Click>> actions = new HashMap<>();

        private Builder(int rows, Component title) {
            this.rows = rows;
            this.title = title;
            this.container = new SimpleContainer(rows * 9);
        }

        public int size() {
            return rows * 9;
        }

        public Builder item(int slot, ItemStack stack) {
            if (slot >= 0 && slot < size()) {
                container.setItem(slot, stack);
            }
            return this;
        }

        public Builder button(int slot, ItemStack stack, Consumer<Click> action) {
            if (slot >= 0 && slot < size()) {
                container.setItem(slot, stack);
                actions.put(slot, action);
            }
            return this;
        }

        /** Fills every empty slot with a nameless gray pane. */
        public Builder fill() {
            for (int i = 0; i < size(); i++) {
                if (container.getItem(i).isEmpty()) {
                    container.setItem(i, icon(Gui.item("minecraft:gray_stained_glass_pane", Items.GLASS_PANE), Component.literal(" "), List.of()));
                }
            }
            return this;
        }

        public void open(ServerPlayer player) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new Gui(type(rows), id, inventory, container, rows, actions), title));
        }
    }

    /** An item with a name and lore lines; the text is shown as given, not in italics. */
    public static ItemStack icon(Item item, Component name, List<? extends Component> lore) {
        return icon(new ItemStack(item), name, lore);
    }

    public static ItemStack icon(ItemStack stack, Component name, List<? extends Component> lore) {
        ItemStack copy = stack.copyWithCount(Math.max(1, Math.min(stack.getCount(), 64)));
        copy.set(DataComponents.CUSTOM_NAME, plain(name));
        copy.set(DataComponents.LORE, new ItemLore(lore.stream().map(line -> (Component) plain(line)).toList()));
        return copy;
    }

    private static MutableComponent plain(Component text) {
        return Component.empty().withStyle(Style.EMPTY.withItalic(false)).append(text);
    }

    /** The item for an id like {@code minecraft:ender_pearl}, or {@code fallback}. */
    public static Item item(String id, Item fallback) {
        Identifier key = id == null ? null : Identifier.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
        return item == null || item == Items.AIR ? fallback : item;
    }

    /** One button in a {@link #paged} list. */
    public record Entry(ItemStack icon, Consumer<Click> action) {
    }

    /**
     * Up to 45 buttons per page, with previous/next arrows and an optional
     * extra button in the bottom row (slot 49, the middle).
     */
    public static void paged(ServerPlayer player, Component title, List<Entry> entries, int page,
            Component previous, Component next, Entry middle) {
        int perPage = 45;
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        int current = Math.max(0, Math.min(page, pages - 1));
        int from = current * perPage;
        int shown = Math.min(perPage, entries.size() - from);
        int rows = Math.max(2, (shown + 8) / 9 + 1);
        Builder builder = builder(rows, title);
        for (int i = 0; i < shown; i++) {
            Entry entry = entries.get(from + i);
            builder.button(i, entry.icon(), entry.action());
        }
        int bottom = (rows - 1) * 9;
        if (current > 0) {
            builder.button(bottom, icon(Items.ARROW, previous, List.of()),
                    click -> paged(click.player(), title, entries, current - 1, previous, next, middle));
        }
        if (current < pages - 1) {
            builder.button(bottom + 8, icon(Items.ARROW, next, List.of()),
                    click -> paged(click.player(), title, entries, current + 1, previous, next, middle));
        }
        if (middle != null) {
            builder.button(bottom + 4, middle.icon(), middle.action());
        }
        builder.open(player);
    }
}
