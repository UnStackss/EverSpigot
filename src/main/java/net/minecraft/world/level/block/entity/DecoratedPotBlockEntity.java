package net.minecraft.world.level.block.entity;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.ticks.ContainerSingleItem;

// CraftBukkit start
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class DecoratedPotBlockEntity extends BlockEntity implements RandomizableContainer, ContainerSingleItem.BlockContainerSingleItem {

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public List<ItemStack> getContents() {
        return Arrays.asList(this.item);
    }

    @Override
    public void onOpen(CraftHumanEntity who) {
        this.transaction.add(who);
    }

    @Override
    public void onClose(CraftHumanEntity who) {
        this.transaction.remove(who);
    }

    @Override
    public List<HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
       return this.maxStack;
    }

    @Override
    public void setMaxStackSize(int i) {
        this.maxStack = i;
    }

    @Override
    public Location getLocation() {
        if (this.level == null) return null;
        return CraftLocation.toBukkit(this.worldPosition, this.level.getWorld());
    }
    // CraftBukkit end

    public static final String TAG_SHERDS = "sherds";
    public static final String TAG_ITEM = "item";
    public static final int EVENT_POT_WOBBLES = 1;
    public long wobbleStartedAtTick;
    @Nullable
    public DecoratedPotBlockEntity.WobbleStyle lastWobbleStyle;
    public PotDecorations decorations;
    private ItemStack item;
    @Nullable
    protected ResourceKey<LootTable> lootTable;
    protected long lootTableSeed;

    public DecoratedPotBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.DECORATED_POT, pos, state);
        this.item = ItemStack.EMPTY;
        this.decorations = PotDecorations.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        this.decorations.save(nbt);
        if (!this.trySaveLootTable(nbt) && !this.item.isEmpty()) {
            nbt.put("item", this.item.save(registryLookup));
        }

    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.decorations = PotDecorations.load(nbt);
        if (!this.tryLoadLootTable(nbt)) {
            if (nbt.contains("item", 10)) {
                this.item = (ItemStack) ItemStack.parse(registryLookup, nbt.getCompound("item")).orElse(ItemStack.EMPTY);
            } else {
                this.item = ItemStack.EMPTY;
            }
        }

    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return this.saveCustomOnly(registryLookup);
    }

    public Direction getDirection() {
        return (Direction) this.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    public PotDecorations getDecorations() {
        return this.decorations;
    }

    public void setFromItem(ItemStack stack) {
        this.applyComponentsFromItemStack(stack);
    }

    public ItemStack getPotAsItem() {
        ItemStack itemstack = Items.DECORATED_POT.getDefaultInstance();

        itemstack.applyComponents(this.collectComponents());
        return itemstack;
    }

    public static ItemStack createDecoratedPotItem(PotDecorations sherds) {
        ItemStack itemstack = Items.DECORATED_POT.getDefaultInstance();

        itemstack.set(DataComponents.POT_DECORATIONS, sherds);
        return itemstack;
    }

    @Nullable
    @Override
    public ResourceKey<LootTable> getLootTable() {
        return this.lootTable;
    }

    @Override
    public void setLootTable(@Nullable ResourceKey<LootTable> lootTable) {
        this.lootTable = lootTable;
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    @Override
    public void setLootTableSeed(long lootTableSeed) {
        this.lootTableSeed = lootTableSeed;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder componentMapBuilder) {
        super.collectImplicitComponents(componentMapBuilder);
        componentMapBuilder.set(DataComponents.POT_DECORATIONS, this.decorations);
        componentMapBuilder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(this.item)));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        this.decorations = (PotDecorations) components.getOrDefault(DataComponents.POT_DECORATIONS, PotDecorations.EMPTY);
        this.item = ((ItemContainerContents) components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)).copyOne();
    }

    @Override
    public void removeComponentsFromTag(CompoundTag nbt) {
        super.removeComponentsFromTag(nbt);
        nbt.remove("sherds");
        nbt.remove("item");
    }

    @Override
    public ItemStack getTheItem() {
        this.unpackLootTable((Player) null);
        return this.item;
    }

    @Override
    public ItemStack splitTheItem(int count) {
        this.unpackLootTable((Player) null);
        ItemStack itemstack = this.item.split(count);

        if (this.item.isEmpty()) {
            this.item = ItemStack.EMPTY;
        }

        return itemstack;
    }

    @Override
    public void setTheItem(ItemStack stack) {
        this.unpackLootTable((Player) null);
        this.item = stack;
    }

    @Override
    public BlockEntity getContainerBlockEntity() {
        return this;
    }

    public void wobble(DecoratedPotBlockEntity.WobbleStyle wobbleType) {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.blockEvent(this.getBlockPos(), this.getBlockState().getBlock(), 1, wobbleType.ordinal());
        }
    }

    @Override
    public boolean triggerEvent(int type, int data) {
        if (this.level != null && type == 1 && data >= 0 && data < DecoratedPotBlockEntity.WobbleStyle.values().length) {
            this.wobbleStartedAtTick = this.level.getGameTime();
            this.lastWobbleStyle = DecoratedPotBlockEntity.WobbleStyle.values()[data];
            return true;
        } else {
            return super.triggerEvent(type, data);
        }
    }

    public static enum WobbleStyle {

        POSITIVE(7), NEGATIVE(10);

        public final int duration;

        private WobbleStyle(final int i) {
            this.duration = i;
        }
    }
}
