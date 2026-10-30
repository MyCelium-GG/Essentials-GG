package my.celium.org.essentialsgg.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import my.celium.org.essentialsgg.data.Loc;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A clickable chest menu made of plain vanilla parts, so vanilla clients open
 * it like any chest. Nothing can be taken out or put in: every click is
 * answered by re-sending the menu, and a button runs its action on the
 * server thread. Opening a menu with the same size and title as the one on
 * screen swaps its contents in place, so the cursor stays where it is.
 *
 * <pre>{@code
 * Gui.builder(3, title)
 *     .button(13, Gui.icon(Items.ENDER_PEARL, name, lore), click -> Homes.go(click.player(), home))
 *     .open(player);
 * }</pre>
 */
public final class Gui extends ChestMenu {
    /** Button action for something unavailable: only a refusal sound. */
    public static final Consumer<Click> LOCKED = click -> { };

    /** Clicks this soon after a menu changed were aimed at the previous one (a double-click). */
    private static final long CLICK_GUARD_MS = 120;
    private static final int COLUMNS = 7;
    private static final int MAX_CONTENT_ROWS = 4;

    private final Component title;
    private final int rows;
    private final int size;
    private Map<Integer, Consumer<Click>> actions;
    private long changedAt = System.currentTimeMillis();

    /** A click on a button: who, and whether it was a right or shift click. */
    public record Click(ServerPlayer player, boolean right, boolean shift) {
    }

    private Gui(MenuType<?> type, int containerId, Inventory inventory, SimpleContainer container, int rows,
            Component title, Map<Integer, Consumer<Click>> actions) {
        super(type, containerId, inventory, container, rows);
        this.title = title;
        this.rows = rows;
        this.actions = actions;
        this.size = rows * 9;
    }

    private void replace(SimpleContainer container, Map<Integer, Consumer<Click>> newActions) {
        for (int i = 0; i < size; i++) {
            getContainer().setItem(i, container.getItem(i));
        }
        actions = newActions;
        changedAt = System.currentTimeMillis();
        sendAllDataToRemote();
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
            if (action != null && System.currentTimeMillis() - changedAt >= CLICK_GUARD_MS) {
                sound(serverPlayer, action == LOCKED ? SoundEvents.NOTE_BLOCK_BASS : SoundEvents.UI_BUTTON_CLICK);
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

    private static void sound(ServerPlayer player, Holder<SoundEvent> sound) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER, player.getX(), player.getY(), player.getZ(),
                0.4f, 1f, player.getRandom().nextLong()));
    }

    /** Plays the refusal sound for an action that turned out not to be possible. */
    public static void deny(ServerPlayer player) {
        sound(player, SoundEvents.NOTE_BLOCK_BASS);
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

        /** A clickable item; a {@code null} action makes it plain decoration. */
        public Builder button(int slot, ItemStack stack, Consumer<Click> action) {
            if (slot >= 0 && slot < size()) {
                container.setItem(slot, stack);
                if (action != null) {
                    actions.put(slot, action);
                }
            }
            return this;
        }

        /** The standard way back, in the bottom left corner. Nothing is added for a {@code null} target. */
        public Builder back(ServerPlayer player, Consumer<ServerPlayer> target) {
            return target == null ? this
                    : button(size() - 9, icon(Items.ARROW, Lang.get(player, "menu.back"), List.of()), click -> target.accept(click.player()));
        }

        /** Fills every empty slot with a gray pane that has no tooltip. */
        public Builder fill() {
            return fill(0);
        }

        /** Fills the empty slots of the bottom row only. */
        public Builder bar() {
            return fill(size() - 9);
        }

        private Builder fill(int from) {
            for (int i = from; i < size(); i++) {
                if (container.getItem(i).isEmpty()) {
                    ItemStack pane = new ItemStack(Gui.item("minecraft:gray_stained_glass_pane", Items.GLASS_PANE));
                    pane.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
                    container.setItem(i, pane);
                }
            }
            return this;
        }

        public void open(ServerPlayer player) {
            if (player.containerMenu instanceof Gui gui && gui.rows == rows && gui.title.equals(title)) {
                gui.replace(container, actions);
                return;
            }
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new Gui(type(rows), id, inventory, container, rows, title, actions), title));
        }
    }

    /** An item with a name and lore lines, without its usual stats; the text is shown as given, not in italics. */
    public static ItemStack icon(Item item, Component name, List<? extends Component> lore) {
        return icon(new ItemStack(item), name, lore);
    }

    public static ItemStack icon(ItemStack stack, Component name, List<? extends Component> lore) {
        ItemStack copy = stack.copyWithCount(Math.max(1, Math.min(stack.getCount(), 64)));
        copy.set(DataComponents.CUSTOM_NAME, plain(name));
        copy.set(DataComponents.LORE, new ItemLore(lore.stream().map(line -> (Component) plain(line)).toList()));
        copy.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));
        return copy;
    }

    /** Marks an icon as the current choice with the enchantment shimmer. */
    public static ItemStack glow(ItemStack stack) {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /** The lore lines for a saved spot: where it is and, in the player's dimension, how far away. */
    public static List<Component> where(ServerPlayer player, Loc loc) {
        List<Component> lines = new ArrayList<>();
        lines.add(Lang.get(player, "menu.location", "x", loc.blockX(), "y", loc.blockY(), "z", loc.blockZ(),
                "world", worldName(player, loc)));
        if (loc.dim().equals(player.level().dimension().identifier().toString())) {
            long blocks = Math.round(Math.sqrt(player.distanceToSqr(loc.x(), loc.y(), loc.z())));
            lines.add(Lang.get(player, "menu.distance", "blocks", blocks));
        }
        return lines;
    }

    private static String worldName(ServerPlayer player, Loc loc) {
        return switch (loc.dimName()) {
            case "overworld" -> Lang.raw(player, "menu.world_overworld");
            case "the_nether" -> Lang.raw(player, "menu.world_nether");
            case "the_end" -> Lang.raw(player, "menu.world_end");
            default -> loc.dimName().replace('_', ' ');
        };
    }

    /** A yes/no question: the answer buttons sit apart so a stray click cannot hit the wrong one. */
    public static void confirm(ServerPlayer player, Component title, Component yes, List<? extends Component> warning, Component no,
            Consumer<ServerPlayer> onYes, Consumer<ServerPlayer> onNo) {
        builder(3, title)
                .button(11, icon(item("minecraft:red_wool", Items.BARRIER), yes, warning), click -> onYes.accept(click.player()))
                .button(15, icon(item("minecraft:lime_wool", Items.ARROW), no, List.of()), click -> onNo.accept(click.player()))
                .fill().open(player);
    }

    /**
     * Columns (1 to 7) for {@code count} buttons centered in a row. An even
     * count leaves the middle free, so the row stays symmetrical.
     */
    public static int[] centered(int count) {
        int half = count / 2;
        int[] columns = new int[count];
        for (int i = 0; i < half; i++) {
            columns[i] = 4 - half + i;
            columns[count - 1 - i] = 4 + half - i;
        }
        if (count % 2 == 1) {
            columns[half] = 4;
        }
        return columns;
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

    /** One button in a {@link #paged} list; a {@code null} action makes it plain decoration. */
    public record Entry(ItemStack icon, Consumer<Click> action) {
    }

    /**
     * A framed list of up to 28 buttons per page, centered, with a bottom row
     * of back (corner, if {@code back} is set), previous, {@code info}, next
     * and {@code action} (other corner). Every page of a longer list has the
     * same size, so the arrows stay under the cursor.
     */
    public static void paged(ServerPlayer player, Component title, List<Entry> entries, int page, ItemStack info,
            Entry action, Consumer<ServerPlayer> back) {
        int perPage = COLUMNS * MAX_CONTENT_ROWS;
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        int current = Math.max(0, Math.min(page, pages - 1));
        int contentRows = pages > 1 ? MAX_CONTENT_ROWS : Math.max(1, (entries.size() + COLUMNS - 1) / COLUMNS);
        Builder builder = builder(contentRows + 2, title);
        int index = current * perPage;
        int end = Math.min(entries.size(), index + perPage);
        for (int row = 1; row <= contentRows && index < end; row++) {
            for (int column : centered(Math.min(COLUMNS, end - index))) {
                Entry entry = entries.get(index++);
                builder.button(row * 9 + column, entry.icon(), entry.action());
            }
        }
        int bottom = builder.size() - 9;
        if (current > 0) {
            builder.button(bottom + 3, icon(Items.ARROW, Lang.get(player, "menu.previous"), List.of()),
                    click -> paged(click.player(), title, entries, current - 1, info, action, back));
        }
        if (current < pages - 1) {
            builder.button(bottom + 5, icon(Items.ARROW, Lang.get(player, "menu.next"), List.of()),
                    click -> paged(click.player(), title, entries, current + 1, info, action, back));
        }
        builder.item(bottom + 4, pageInfo(player, info, current, pages));
        if (action != null) {
            builder.button(bottom + 8, action.icon(), action.action());
        }
        builder.back(player, back).fill().open(player);
    }

    /** {@code info} with the page number as an extra line once there is more than one page. */
    private static ItemStack pageInfo(ServerPlayer player, ItemStack info, int current, int pages) {
        if (pages <= 1) {
            return info == null ? ItemStack.EMPTY : info;
        }
        Component line = Lang.get(player, "menu.page", "page", current + 1, "pages", pages);
        if (info == null) {
            return icon(Items.PAPER, line, List.of());
        }
        ItemStack copy = info.copy();
        List<Component> lore = new ArrayList<>(copy.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lore.add(plain(line));
        copy.set(DataComponents.LORE, new ItemLore(lore));
        return copy;
    }
}
