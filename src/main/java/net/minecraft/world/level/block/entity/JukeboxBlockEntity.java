package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;

// CraftBukkit start
import java.util.Collections;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class JukeboxBlockEntity extends BlockEntity implements Clearable, ContainerSingleItem.BlockContainerSingleItem {

    public static final String SONG_ITEM_TAG_ID = "RecordItem";
    public static final String TICKS_SINCE_SONG_STARTED_TAG_ID = "ticks_since_song_started";
    private ItemStack item;
    private final JukeboxSongPlayer jukeboxSongPlayer;
    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;
    public boolean opened;

    @Override
    public List<ItemStack> getContents() {
        return Collections.singletonList(this.item);
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
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    @Override
    public Location getLocation() {
        if (this.level == null) return null;
        return new org.bukkit.Location(this.level.getWorld(), this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
    }
    // CraftBukkit end

    public JukeboxBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.JUKEBOX, pos, state);
        this.item = ItemStack.EMPTY;
        this.jukeboxSongPlayer = new JukeboxSongPlayer(this::onSongChanged, this.getBlockPos());
    }

    public JukeboxSongPlayer getSongPlayer() {
        return this.jukeboxSongPlayer;
    }

    public void onSongChanged() {
        this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        this.setChanged();
    }

    private void notifyItemChangedInJukebox(boolean hasRecord) {
        if (this.level != null && this.level.getBlockState(this.getBlockPos()) == this.getBlockState()) {
            this.level.setBlock(this.getBlockPos(), (BlockState) this.getBlockState().setValue(JukeboxBlock.HAS_RECORD, hasRecord), 2);
            this.level.gameEvent((Holder) GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(this.getBlockState()));
        }
    }

    public void popOutTheItem() {
        if (this.level != null && !this.level.isClientSide) {
            BlockPos blockposition = this.getBlockPos();
            ItemStack itemstack = this.getTheItem();

            if (!itemstack.isEmpty()) {
                this.removeTheItem();
                Vec3 vec3d = Vec3.atLowerCornerWithOffset(blockposition, 0.5D, 1.01D, 0.5D).offsetRandom(this.level.random, 0.7F);
                ItemStack itemstack1 = itemstack.copy();
                ItemEntity entityitem = new ItemEntity(this.level, vec3d.x(), vec3d.y(), vec3d.z(), itemstack1);

                entityitem.setDefaultPickUpDelay();
                this.level.addFreshEntity(entityitem);
            }
        }
    }

    public static void tick(Level world, BlockPos pos, BlockState state, JukeboxBlockEntity blockEntity) {
        blockEntity.jukeboxSongPlayer.tick(world, state);
    }

    public int getComparatorOutput() {
        return (Integer) JukeboxSong.fromStack(this.level.registryAccess(), this.item).map(Holder::value).map(JukeboxSong::comparatorOutput).orElse(0);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        if (nbt.contains("RecordItem", 10)) {
            this.item = (ItemStack) ItemStack.parse(registryLookup, nbt.getCompound("RecordItem")).orElse(ItemStack.EMPTY);
        } else {
            this.item = ItemStack.EMPTY;
        }

        if (nbt.contains("ticks_since_song_started", 4)) {
            JukeboxSong.fromStack(registryLookup, this.item).ifPresent((holder) -> {
                this.jukeboxSongPlayer.setSongWithoutPlaying(holder, nbt.getLong("ticks_since_song_started"));
            });
        }

    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        if (!this.getTheItem().isEmpty()) {
            nbt.put("RecordItem", this.getTheItem().save(registryLookup));
        }

        if (this.jukeboxSongPlayer.getSong() != null) {
            nbt.putLong("ticks_since_song_started", this.jukeboxSongPlayer.getTicksSinceSongStarted());
        }

    }

    @Override
    public ItemStack getTheItem() {
        return this.item;
    }

    @Override
    public ItemStack splitTheItem(int count) {
        ItemStack itemstack = this.item;

        this.setTheItem(ItemStack.EMPTY);
        return itemstack;
    }

    @Override
    public void setTheItem(ItemStack stack) {
        this.item = stack;
        boolean flag = !this.item.isEmpty();
        Optional<Holder<JukeboxSong>> optional = JukeboxSong.fromStack(this.level.registryAccess(), this.item);

        this.notifyItemChangedInJukebox(flag);
        if (flag && optional.isPresent()) {
            this.jukeboxSongPlayer.play(this.level, (Holder) optional.get());
        } else {
            this.jukeboxSongPlayer.stop(this.level, this.getBlockState());
        }

    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack; // CraftBukkit
    }

    @Override
    public BlockEntity getContainerBlockEntity() {
        return this;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.has(DataComponents.JUKEBOX_PLAYABLE) && this.getItem(slot).isEmpty();
    }

    @Override
    public boolean canTakeItem(Container hopperInventory, int slot, ItemStack stack) {
        return hopperInventory.hasAnyMatching(ItemStack::isEmpty);
    }

    @VisibleForTesting
    public void setSongItemWithoutPlaying(ItemStack itemstack, long ticksSinceSongStarted) { // CraftBukkit - add argument
        this.item = itemstack;
        this.jukeboxSongPlayer.song = null; // CraftBukkit - reset
        JukeboxSong.fromStack(this.level != null ? this.level.registryAccess() : org.bukkit.craftbukkit.CraftRegistry.getMinecraftRegistry(), itemstack).ifPresent((holder) -> { // Paper - fallback to other RegistyrAccess if no level
            this.jukeboxSongPlayer.setSongWithoutPlaying(holder, ticksSinceSongStarted); // CraftBukkit - add argument
        });
        // CraftBukkit start - add null check for level
        if (this.level != null) {
            this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        }
        // CraftBukkit end
        this.setChanged();
    }

    @VisibleForTesting
    public void tryForcePlaySong() {
        JukeboxSong.fromStack(this.level.registryAccess(), this.getTheItem()).ifPresent((holder) -> {
            this.jukeboxSongPlayer.play(this.level, holder);
        });
    }
}
