package net.minecraft.world.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.ClientSideMerchant;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.bukkit.craftbukkit.inventory.view.CraftMerchantView; // CraftBukkit

public class MerchantMenu extends AbstractContainerMenu {

    protected static final int PAYMENT1_SLOT = 0;
    protected static final int PAYMENT2_SLOT = 1;
    protected static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    private static final int SELLSLOT1_X = 136;
    private static final int SELLSLOT2_X = 162;
    private static final int BUYSLOT_X = 220;
    private static final int ROW_Y = 37;
    private final Merchant trader;
    private final MerchantContainer tradeContainer;
    private int merchantLevel;
    private boolean showProgressBar;
    private boolean canRestock;

    // CraftBukkit start
    private CraftMerchantView bukkitEntity = null;
    private Inventory player;

    @Override
    public CraftMerchantView getBukkitView() {
        if (this.bukkitEntity == null) {
            this.bukkitEntity = new CraftMerchantView(this.player.player.getBukkitEntity(), new org.bukkit.craftbukkit.inventory.CraftInventoryMerchant(this.trader, this.tradeContainer), this, this.trader);
        }
        return this.bukkitEntity;
    }
    // CraftBukkit end

    public MerchantMenu(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, new ClientSideMerchant(playerInventory.player));
    }

    public MerchantMenu(int syncId, Inventory playerInventory, Merchant merchant) {
        super(MenuType.MERCHANT, syncId);
        this.trader = merchant;
        this.tradeContainer = new MerchantContainer(merchant);
        this.addSlot(new Slot(this.tradeContainer, 0, 136, 37));
        this.addSlot(new Slot(this.tradeContainer, 1, 162, 37));
        this.addSlot(new MerchantResultSlot(playerInventory.player, merchant, this.tradeContainer, 2, 220, 37));
        this.player = playerInventory; // CraftBukkit - save player

        int j;

        for (j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerInventory, k + j * 9 + 9, 108 + k * 18, 84 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerInventory, j, 108 + j * 18, 142));
        }

    }

    public void setShowProgressBar(boolean leveled) {
        this.showProgressBar = leveled;
    }

    @Override
    public void slotsChanged(Container inventory) {
        this.tradeContainer.updateSellItem();
        super.slotsChanged(inventory);
    }

    public void setSelectionHint(int index) {
        this.tradeContainer.setSelectionHint(index);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.trader.getTradingPlayer() == player;
    }

    public int getTraderXp() {
        return this.trader.getVillagerXp();
    }

    public int getFutureTraderXp() {
        return this.tradeContainer.getFutureXp();
    }

    public void setXp(int experience) {
        this.trader.overrideXp(experience);
    }

    public int getTraderLevel() {
        return this.merchantLevel;
    }

    public void setMerchantLevel(int levelProgress) {
        this.merchantLevel = levelProgress;
    }

    public void setCanRestock(boolean canRefreshTrades) {
        this.canRestock = canRefreshTrades;
    }

    public boolean canRestock() {
        return this.canRestock;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot1 = (Slot) this.slots.get(slot);

        if (slot1 != null && slot1.hasItem()) {
            ItemStack itemstack1 = slot1.getItem();

            itemstack = itemstack1.copy();
            if (slot == 2) {
                if (!this.moveItemStackTo(itemstack1, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot1.onQuickCraft(itemstack1, itemstack);
                this.playTradeSound();
            } else if (slot != 0 && slot != 1) {
                if (slot >= 3 && slot < 30) {
                    if (!this.moveItemStackTo(itemstack1, 30, 39, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slot >= 30 && slot < 39 && !this.moveItemStackTo(itemstack1, 3, 30, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 3, 39, false)) {
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

    private void playTradeSound() {
        if (!this.trader.isClientSide() && this.trader instanceof Entity) { // CraftBukkit - SPIGOT-5035
            Entity entity = (Entity) this.trader;

            entity.level().playLocalSound(entity.getX(), entity.getY(), entity.getZ(), this.trader.getNotifyTradeSound(), SoundSource.NEUTRAL, 1.0F, 1.0F, false);
        }

    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.trader.setTradingPlayer((Player) null);
        if (!this.trader.isClientSide()) {
            if (player.isAlive() && (!(player instanceof ServerPlayer) || !((ServerPlayer) player).hasDisconnected())) {
                if (player instanceof ServerPlayer) {
                    player.getInventory().placeItemBackInInventory(this.tradeContainer.removeItemNoUpdate(0));
                    player.getInventory().placeItemBackInInventory(this.tradeContainer.removeItemNoUpdate(1));
                }
            } else {
                ItemStack itemstack = this.tradeContainer.removeItemNoUpdate(0);

                if (!itemstack.isEmpty()) {
                    player.drop(itemstack, false);
                }

                itemstack = this.tradeContainer.removeItemNoUpdate(1);
                if (!itemstack.isEmpty()) {
                    player.drop(itemstack, false);
                }
            }

        }
    }

    public void tryMoveItems(int recipeIndex) {
        if (recipeIndex >= 0 && this.getOffers().size() > recipeIndex) {
            ItemStack itemstack = this.tradeContainer.getItem(0);

            if (!itemstack.isEmpty()) {
                if (!this.moveItemStackTo(itemstack, 3, 39, true)) {
                    return;
                }

                this.tradeContainer.setItem(0, itemstack);
            }

            ItemStack itemstack1 = this.tradeContainer.getItem(1);

            if (!itemstack1.isEmpty()) {
                if (!this.moveItemStackTo(itemstack1, 3, 39, true)) {
                    return;
                }

                this.tradeContainer.setItem(1, itemstack1);
            }

            if (this.tradeContainer.getItem(0).isEmpty() && this.tradeContainer.getItem(1).isEmpty()) {
                MerchantOffer merchantrecipe = (MerchantOffer) this.getOffers().get(recipeIndex);

                this.moveFromInventoryToPaymentSlot(0, merchantrecipe.getItemCostA());
                merchantrecipe.getItemCostB().ifPresent((itemcost) -> {
                    this.moveFromInventoryToPaymentSlot(1, itemcost);
                });
            }

        }
    }

    private void moveFromInventoryToPaymentSlot(int slot, ItemCost stack) {
        for (int j = 3; j < 39; ++j) {
            ItemStack itemstack = ((Slot) this.slots.get(j)).getItem();

            if (!itemstack.isEmpty() && stack.test(itemstack)) {
                ItemStack itemstack1 = this.tradeContainer.getItem(slot);

                if (itemstack1.isEmpty() || ItemStack.isSameItemSameComponents(itemstack, itemstack1)) {
                    int k = itemstack.getMaxStackSize();
                    int l = Math.min(k - itemstack1.getCount(), itemstack.getCount());
                    ItemStack itemstack2 = itemstack.copyWithCount(itemstack1.getCount() + l);

                    itemstack.shrink(l);
                    this.tradeContainer.setItem(slot, itemstack2);
                    if (itemstack2.getCount() >= k) {
                        break;
                    }
                }
            }
        }

    }

    public void setOffers(MerchantOffers offers) {
        this.trader.overrideOffers(offers);
    }

    public MerchantOffers getOffers() {
        return this.trader.getOffers();
    }

    public boolean showProgressBar() {
        return this.showProgressBar;
    }
}
