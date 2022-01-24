package net.minecraft.world.inventory;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BannerPatternTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BannerPatternItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.inventory.CraftInventoryLoom;
import org.bukkit.craftbukkit.inventory.view.CraftLoomView;
import org.bukkit.entity.Player;
// CraftBukkit end

public class LoomMenu extends AbstractContainerMenu {

    // CraftBukkit start
    private CraftLoomView bukkitEntity = null;
    private Player player;

    @Override
    public CraftLoomView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        CraftInventoryLoom inventory = new CraftInventoryLoom(this.inputContainer, this.outputContainer);
        this.bukkitEntity = new CraftLoomView(this.player, inventory, this);
        return this.bukkitEntity;
    }
    // CraftBukkit end
    private static final int PATTERN_NOT_SET = -1;
    private static final int INV_SLOT_START = 4;
    private static final int INV_SLOT_END = 31;
    private static final int USE_ROW_SLOT_START = 31;
    private static final int USE_ROW_SLOT_END = 40;
    private final ContainerLevelAccess access;
    final DataSlot selectedBannerPatternIndex;
    private List<Holder<BannerPattern>> selectablePatterns;
    Runnable slotUpdateListener;
    private final HolderGetter<BannerPattern> patternGetter;
    final Slot bannerSlot;
    final Slot dyeSlot;
    private final Slot patternSlot;
    private final Slot resultSlot;
    long lastSoundTime;
    private final Container inputContainer;
    private final Container outputContainer;

    public LoomMenu(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, ContainerLevelAccess.NULL);
    }

    public LoomMenu(int syncId, Inventory playerInventory, final ContainerLevelAccess context) {
        super(MenuType.LOOM, syncId);
        this.selectedBannerPatternIndex = DataSlot.standalone();
        this.selectablePatterns = List.of();
        this.slotUpdateListener = () -> {
        };
        this.inputContainer = new SimpleContainer(this.createBlockHolder(context), 3) { // Paper - Add missing InventoryHolders
            @Override
            public void setChanged() {
                super.setChanged();
                LoomMenu.this.slotsChanged(this);
                LoomMenu.this.slotUpdateListener.run();
            }

            // CraftBukkit start
            @Override
            public Location getLocation() {
                return context.getLocation();
            }
            // CraftBukkit end
        };
        this.outputContainer = new SimpleContainer(this.createBlockHolder(context), 1) { // Paper - Add missing InventoryHolders
            @Override
            public void setChanged() {
                super.setChanged();
                LoomMenu.this.slotUpdateListener.run();
            }

            // CraftBukkit start
            @Override
            public Location getLocation() {
                return context.getLocation();
            }
            // CraftBukkit end
        };
        this.access = context;
        this.bannerSlot = this.addSlot(new Slot(this.inputContainer, 0, 13, 26) { // CraftBukkit - decompile error
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BannerItem;
            }
        });
        this.dyeSlot = this.addSlot(new Slot(this.inputContainer, 1, 33, 26) { // CraftBukkit - decompile error
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DyeItem;
            }
        });
        this.patternSlot = this.addSlot(new Slot(this.inputContainer, 2, 23, 45) { // CraftBukkit - decompile error
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BannerPatternItem;
            }
        });
        this.resultSlot = this.addSlot(new Slot(this.outputContainer, 0, 143, 57) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(net.minecraft.world.entity.player.Player player, ItemStack stack) {
                LoomMenu.this.bannerSlot.remove(1);
                LoomMenu.this.dyeSlot.remove(1);
                if (!LoomMenu.this.bannerSlot.hasItem() || !LoomMenu.this.dyeSlot.hasItem()) {
                    LoomMenu.this.selectedBannerPatternIndex.set(-1);
                }

                context.execute((world, blockposition) -> {
                    long j = world.getGameTime();

                    if (LoomMenu.this.lastSoundTime != j) {
                        world.playSound((net.minecraft.world.entity.player.Player) null, blockposition, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        LoomMenu.this.lastSoundTime = j;
                    }

                });
                super.onTake(player, stack);
            }
        });

        int j;

        for (j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerInventory, k + j * 9 + 9, 8 + k * 18, 84 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerInventory, j, 8 + j * 18, 142));
        }

        this.addDataSlot(this.selectedBannerPatternIndex);
        this.patternGetter = playerInventory.player.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        this.player = (Player) playerInventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.LOOM);
    }

    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player player, int id) {
        if (id >= 0 && id < this.selectablePatterns.size()) {
            // Paper start - Add PlayerLoomPatternSelectEvent
            int selectablePatternIndex = id;
            io.papermc.paper.event.player.PlayerLoomPatternSelectEvent event = new io.papermc.paper.event.player.PlayerLoomPatternSelectEvent((Player) player.getBukkitEntity(), ((CraftInventoryLoom) getBukkitView().getTopInventory()), org.bukkit.craftbukkit.block.banner.CraftPatternType.minecraftHolderToBukkit(this.selectablePatterns.get(selectablePatternIndex)));
            if (!event.callEvent()) {
                player.containerMenu.sendAllDataToRemote();
                return false;
            }
            final Holder<BannerPattern> eventPattern = org.bukkit.craftbukkit.block.banner.CraftPatternType.bukkitToMinecraftHolder(event.getPatternType());
            Holder<BannerPattern> selectedPattern = null;
            for (int i = 0; i < this.selectablePatterns.size(); i++) {
                final Holder<BannerPattern> holder = this.selectablePatterns.get(i);
                if (eventPattern.equals(holder)) {
                    selectablePatternIndex = i;
                    selectedPattern = holder;
                    break;
                }
            }
            if (selectedPattern == null) {
                selectedPattern = eventPattern;
                selectablePatternIndex = -1;
            }

            player.containerMenu.sendAllDataToRemote();
            this.selectedBannerPatternIndex.set(selectablePatternIndex);
            this.setupResultSlot(java.util.Objects.requireNonNull(selectedPattern, "selectedPattern was null, this is unexpected"));
            // Paper end - Add PlayerLoomPatternSelectEvent
            return true;
        } else {
            return false;
        }
    }

    private List<Holder<BannerPattern>> getSelectablePatterns(ItemStack stack) {
        if (stack.isEmpty()) {
            return (List) this.patternGetter.get(BannerPatternTags.NO_ITEM_REQUIRED).map(ImmutableList::copyOf).orElse(ImmutableList.of());
        } else {
            Item item = stack.getItem();

            if (item instanceof BannerPatternItem) {
                BannerPatternItem itembannerpattern = (BannerPatternItem) item;

                return (List) this.patternGetter.get(itembannerpattern.getBannerPattern()).map(ImmutableList::copyOf).orElse(ImmutableList.of());
            } else {
                return List.of();
            }
        }
    }

    private boolean isValidPatternIndex(int index) {
        return index >= 0 && index < this.selectablePatterns.size();
    }

    @Override
    public void slotsChanged(Container inventory) {
        ItemStack itemstack = this.bannerSlot.getItem();
        ItemStack itemstack1 = this.dyeSlot.getItem();
        ItemStack itemstack2 = this.patternSlot.getItem();

        if (!itemstack.isEmpty() && !itemstack1.isEmpty()) {
            int i = this.selectedBannerPatternIndex.get();
            boolean flag = this.isValidPatternIndex(i);
            List<Holder<BannerPattern>> list = this.selectablePatterns;

            this.selectablePatterns = this.getSelectablePatterns(itemstack2);
            Holder holder;

            if (this.selectablePatterns.size() == 1) {
                this.selectedBannerPatternIndex.set(0);
                holder = (Holder) this.selectablePatterns.get(0);
            } else if (!flag) {
                this.selectedBannerPatternIndex.set(-1);
                holder = null;
            } else {
                Holder<BannerPattern> holder1 = (Holder) list.get(i);
                int j = this.selectablePatterns.indexOf(holder1);

                if (j != -1) {
                    holder = holder1;
                    this.selectedBannerPatternIndex.set(j);
                } else {
                    holder = null;
                    this.selectedBannerPatternIndex.set(-1);
                }
            }

            if (holder != null) {
                BannerPatternLayers bannerpatternlayers = (BannerPatternLayers) itemstack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
                boolean flag1 = bannerpatternlayers.layers().size() >= 6;

                if (flag1) {
                    this.selectedBannerPatternIndex.set(-1);
                    this.resultSlot.set(ItemStack.EMPTY);
                } else {
                    this.setupResultSlot(holder);
                }
            } else {
                this.resultSlot.set(ItemStack.EMPTY);
            }

            // this.broadcastChanges(); // Paper - Add PrepareResultEvent; done below
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, 3); // Paper - Add PrepareResultEvent
        } else {
            this.resultSlot.set(ItemStack.EMPTY);
            this.selectablePatterns = List.of();
            this.selectedBannerPatternIndex.set(-1);
        }
    }

    public List<Holder<BannerPattern>> getSelectablePatterns() {
        return this.selectablePatterns;
    }

    public int getSelectedBannerPatternIndex() {
        return this.selectedBannerPatternIndex.get();
    }

    public void registerUpdateListener(Runnable inventoryChangeListener) {
        this.slotUpdateListener = inventoryChangeListener;
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot1 = (Slot) this.slots.get(slot);

        if (slot1 != null && slot1.hasItem()) {
            ItemStack itemstack1 = slot1.getItem();

            itemstack = itemstack1.copy();
            if (slot == this.resultSlot.index) {
                if (!this.moveItemStackTo(itemstack1, 4, 40, true)) {
                    return ItemStack.EMPTY;
                }

                slot1.onQuickCraft(itemstack1, itemstack);
            } else if (slot != this.dyeSlot.index && slot != this.bannerSlot.index && slot != this.patternSlot.index) {
                if (itemstack1.getItem() instanceof BannerItem) {
                    if (!this.moveItemStackTo(itemstack1, this.bannerSlot.index, this.bannerSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (itemstack1.getItem() instanceof DyeItem) {
                    if (!this.moveItemStackTo(itemstack1, this.dyeSlot.index, this.dyeSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (itemstack1.getItem() instanceof BannerPatternItem) {
                    if (!this.moveItemStackTo(itemstack1, this.patternSlot.index, this.patternSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slot >= 4 && slot < 31) {
                    if (!this.moveItemStackTo(itemstack1, 31, 40, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slot >= 31 && slot < 40 && !this.moveItemStackTo(itemstack1, 4, 31, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 4, 40, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot1.setByPlayer(ItemStack.EMPTY);
            } else {
                slot1.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot1.onTake(player, itemstack1);
        }

        return itemstack;
    }

    @Override
    public void removed(net.minecraft.world.entity.player.Player player) {
        super.removed(player);
        this.access.execute((world, blockposition) -> {
            this.clearContainer(player, this.inputContainer);
        });
    }

    private void setupResultSlot(Holder<BannerPattern> pattern) {
        ItemStack itemstack = this.bannerSlot.getItem();
        ItemStack itemstack1 = this.dyeSlot.getItem();
        ItemStack itemstack2 = ItemStack.EMPTY;

        if (!itemstack.isEmpty() && !itemstack1.isEmpty()) {
            itemstack2 = itemstack.copyWithCount(1);
            DyeColor enumcolor = ((DyeItem) itemstack1.getItem()).getDyeColor();

            itemstack2.update(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY, (bannerpatternlayers) -> {
                // CraftBukkit start
                if (bannerpatternlayers.layers().size() > 20) {
                    bannerpatternlayers = new BannerPatternLayers(List.copyOf(bannerpatternlayers.layers().subList(0, 20)));
                }
                // CraftBukkit end
                return (new BannerPatternLayers.Builder()).addAll(bannerpatternlayers).add(pattern, enumcolor).build();
            });
        }

        if (!ItemStack.matches(itemstack2, this.resultSlot.getItem())) {
            this.resultSlot.set(itemstack2);
        }

    }

    public Slot getBannerSlot() {
        return this.bannerSlot;
    }

    public Slot getDyeSlot() {
        return this.dyeSlot;
    }

    public Slot getPatternSlot() {
        return this.patternSlot;
    }

    public Slot getResultSlot() {
        return this.resultSlot;
    }
}
