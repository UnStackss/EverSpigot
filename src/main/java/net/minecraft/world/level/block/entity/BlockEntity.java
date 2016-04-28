package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

import org.spigotmc.CustomTimingsHandler; // Spigot
import co.aikar.timings.MinecraftTimings; // Paper
import co.aikar.timings.Timing; // Paper

public abstract class BlockEntity {
    static boolean ignoreTileUpdates; // Paper - Perf: Optimize Hoppers

    public Timing tickTimer = MinecraftTimings.getTileEntityTimings(this); // Paper
    // CraftBukkit start - data containers
    private static final CraftPersistentDataTypeRegistry DATA_TYPE_REGISTRY = new CraftPersistentDataTypeRegistry();
    public CraftPersistentDataContainer persistentDataContainer;
    // CraftBukkit end
    private static final Logger LOGGER = LogUtils.getLogger();
    private final BlockEntityType<?> type;
    @Nullable
    protected Level level;
    protected final BlockPos worldPosition;
    protected boolean remove;
    private BlockState blockState;
    private DataComponentMap components;

    public BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        this.components = DataComponentMap.EMPTY;
        this.type = type;
        this.worldPosition = pos.immutable();
        this.validateBlockState(state);
        this.blockState = state;
        this.persistentDataContainer = new CraftPersistentDataContainer(DATA_TYPE_REGISTRY); // Paper - always init
    }

    private void validateBlockState(BlockState state) {
        if (!this.isValidBlockState(state)) {
            String s = this.getNameForReporting();

            throw new IllegalStateException("Invalid block entity " + s + " state at " + String.valueOf(this.worldPosition) + ", got " + String.valueOf(state));
        }
    }

    public boolean isValidBlockState(BlockState state) {
        return this.type.isValid(state);
    }

    public static BlockPos getPosFromTag(CompoundTag nbt) {
        return new BlockPos(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z"));
    }

    @Nullable
    public Level getLevel() {
        return this.level;
    }

    public void setLevel(Level world) {
        this.level = world;
    }

    public boolean hasLevel() {
        return this.level != null;
    }

    // CraftBukkit start - read container
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        this.persistentDataContainer.clear(); // Paper - clear instead of init

        net.minecraft.nbt.Tag persistentDataTag = nbt.get("PublicBukkitValues");
        if (persistentDataTag instanceof CompoundTag) {
            this.persistentDataContainer.putAll((CompoundTag) persistentDataTag);
        }
    }
    // CraftBukkit end

    public final void loadWithComponents(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        this.loadAdditional(nbt, registryLookup);
        BlockEntity.ComponentHelper.COMPONENTS_CODEC.parse(registryLookup.createSerializationContext(NbtOps.INSTANCE), nbt).resultOrPartial((s) -> {
            BlockEntity.LOGGER.warn("Failed to load components: {}", s);
        }).ifPresent((datacomponentmap) -> {
            this.components = datacomponentmap;
        });
    }

    public final void loadCustomOnly(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        this.loadAdditional(nbt, registryLookup);
    }

    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {}

    public final CompoundTag saveWithFullMetadata(HolderLookup.Provider registryLookup) {
        CompoundTag nbttagcompound = this.saveWithoutMetadata(registryLookup);

        this.saveMetadata(nbttagcompound);
        return nbttagcompound;
    }

    public final CompoundTag saveWithId(HolderLookup.Provider registryLookup) {
        CompoundTag nbttagcompound = this.saveWithoutMetadata(registryLookup);

        this.saveId(nbttagcompound);
        return nbttagcompound;
    }

    public final CompoundTag saveWithoutMetadata(HolderLookup.Provider registryLookup) {
        CompoundTag nbttagcompound = new CompoundTag();

        this.saveAdditional(nbttagcompound, registryLookup);
        BlockEntity.ComponentHelper.COMPONENTS_CODEC.encodeStart(registryLookup.createSerializationContext(NbtOps.INSTANCE), this.components).resultOrPartial((s) -> {
            BlockEntity.LOGGER.warn("Failed to save components: {}", s);
        }).ifPresent((nbtbase) -> {
            nbttagcompound.merge((CompoundTag) nbtbase);
        });
        // CraftBukkit start - store container
        if (this.persistentDataContainer != null && !this.persistentDataContainer.isEmpty()) {
            nbttagcompound.put("PublicBukkitValues", this.persistentDataContainer.toTagCompound());
        }
        // CraftBukkit end
        return nbttagcompound;
    }

    public final CompoundTag saveCustomOnly(HolderLookup.Provider registryLookup) {
        CompoundTag nbttagcompound = new CompoundTag();

        this.saveAdditional(nbttagcompound, registryLookup);
        // Paper start - store PDC here as well
        if (this.persistentDataContainer != null && !this.persistentDataContainer.isEmpty()) {
            nbttagcompound.put("PublicBukkitValues", this.persistentDataContainer.toTagCompound());
        }
        // Paper end
        return nbttagcompound;
    }

    public final CompoundTag saveCustomAndMetadata(HolderLookup.Provider registryLookup) {
        CompoundTag nbttagcompound = this.saveCustomOnly(registryLookup);

        this.saveMetadata(nbttagcompound);
        return nbttagcompound;
    }

    public void saveId(CompoundTag nbt) {
        ResourceLocation minecraftkey = BlockEntityType.getKey(this.getType());

        if (minecraftkey == null) {
            throw new RuntimeException(String.valueOf(this.getClass()) + " is missing a mapping! This is a bug!");
        } else {
            nbt.putString("id", minecraftkey.toString());
        }
    }

    public static void addEntityType(CompoundTag nbt, BlockEntityType<?> type) {
        nbt.putString("id", BlockEntityType.getKey(type).toString());
    }

    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag nbttagcompound = this.saveCustomOnly(registries);

        this.removeComponentsFromTag(nbttagcompound);
        BlockItem.setBlockEntityData(stack, this.getType(), nbttagcompound);
        stack.applyComponents(this.collectComponents());
    }

    private void saveMetadata(CompoundTag nbt) {
        this.saveId(nbt);
        nbt.putInt("x", this.worldPosition.getX());
        nbt.putInt("y", this.worldPosition.getY());
        nbt.putInt("z", this.worldPosition.getZ());
    }

    @Nullable
    public static BlockEntity loadStatic(BlockPos pos, BlockState state, CompoundTag nbt, HolderLookup.Provider registryLookup) {
        String s = nbt.getString("id");
        ResourceLocation minecraftkey = ResourceLocation.tryParse(s);

        if (minecraftkey == null) {
            BlockEntity.LOGGER.error("Block entity has invalid type: {}", s);
            return null;
        } else {
            return (BlockEntity) BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(minecraftkey).map((tileentitytypes) -> {
                try {
                    return tileentitytypes.create(pos, state);
                } catch (Throwable throwable) {
                    BlockEntity.LOGGER.error("Failed to create block entity {}", s, throwable);
                    return null;
                }
            }).map((tileentity) -> {
                try {
                    tileentity.loadWithComponents(nbt, registryLookup);
                    return tileentity;
                } catch (Throwable throwable) {
                    BlockEntity.LOGGER.error("Failed to load data for block entity {}", s, throwable);
                    return null;
                }
            }).orElseGet(() -> {
                BlockEntity.LOGGER.warn("Skipping BlockEntity with id {}", s);
                return null;
            });
        }
    }

    public void setChanged() {
        if (this.level != null) {
            if (ignoreTileUpdates) return; // Paper - Perf: Optimize Hoppers
            BlockEntity.setChanged(this.level, this.worldPosition, this.blockState);
        }

    }

    protected static void setChanged(Level world, BlockPos pos, BlockState state) {
        world.blockEntityChanged(pos);
        if (!state.isAir()) {
            world.updateNeighbourForOutputSignal(pos, state.getBlock());
        }

    }

    public BlockPos getBlockPos() {
        return this.worldPosition;
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return null;
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return new CompoundTag();
    }

    public boolean isRemoved() {
        return this.remove;
    }

    public void setRemoved() {
        this.remove = true;
    }

    public void clearRemoved() {
        this.remove = false;
    }

    public boolean triggerEvent(int type, int data) {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory crashReportSection) {
        crashReportSection.setDetail("Name", this::getNameForReporting);
        if (this.level != null) {
            // Paper start - Prevent block entity and entity crashes
            BlockState block = this.getBlockState();
            if (block != null) {
                CrashReportCategory.populateBlockDetails(crashReportSection, this.level, this.worldPosition, block);
            }
            // Paper end - Prevent block entity and entity crashes
            CrashReportCategory.populateBlockDetails(crashReportSection, this.level, this.worldPosition, this.level.getBlockState(this.worldPosition));
        }
    }

    private String getNameForReporting() {
        String s = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(this.getType()));

        return s + " // " + this.getClass().getCanonicalName();
    }

    public boolean onlyOpCanSetNbt() {
        return false;
    }

    public BlockEntityType<?> getType() {
        return this.type;
    }

    /** @deprecated */
    @Deprecated
    public void setBlockState(BlockState state) {
        this.validateBlockState(state);
        this.blockState = state;
    }

    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {}

    public final void applyComponentsFromItemStack(ItemStack stack) {
        this.applyComponents(stack.getPrototype(), stack.getComponentsPatch());
    }

    public final void applyComponents(DataComponentMap defaultComponents, DataComponentPatch components) {
        // CraftBukkit start
        this.applyComponentsSet(defaultComponents, components);
    }

    public final Set<DataComponentType<?>> applyComponentsSet(DataComponentMap datacomponentmap, DataComponentPatch datacomponentpatch) {
        // CraftBukkit end
        final Set<DataComponentType<?>> set = new HashSet();

        set.add(DataComponents.BLOCK_ENTITY_DATA);
        final PatchedDataComponentMap patcheddatacomponentmap = PatchedDataComponentMap.fromPatch(datacomponentmap, datacomponentpatch);

        this.applyImplicitComponents(new BlockEntity.DataComponentInput() { // CraftBukkit - decompile error
            @Nullable
            @Override
            public <T> T get(DataComponentType<T> type) {
                set.add(type);
                return patcheddatacomponentmap.get(type);
            }

            @Override
            public <T> T getOrDefault(DataComponentType<? extends T> type, T fallback) {
                set.add(type);
                return patcheddatacomponentmap.getOrDefault(type, fallback);
            }
        });
        Objects.requireNonNull(set);
        DataComponentPatch datacomponentpatch1 = datacomponentpatch.forget(set::contains);

        this.components = datacomponentpatch1.split().added();
        // CraftBukkit start
        set.remove(DataComponents.BLOCK_ENTITY_DATA); // Remove as never actually added by applyImplicitComponents
        return set;
        // CraftBukkit end
    }

    protected void collectImplicitComponents(DataComponentMap.Builder componentMapBuilder) {}

    /** @deprecated */
    @Deprecated
    public void removeComponentsFromTag(CompoundTag nbt) {}

    public final DataComponentMap collectComponents() {
        DataComponentMap.Builder datacomponentmap_a = DataComponentMap.builder();

        datacomponentmap_a.addAll(this.components);
        this.collectImplicitComponents(datacomponentmap_a);
        return datacomponentmap_a.build();
    }

    public DataComponentMap components() {
        return this.components;
    }

    public void setComponents(DataComponentMap components) {
        this.components = components;
    }

    @Nullable
    public static Component parseCustomNameSafe(String json, HolderLookup.Provider registryLookup) {
        try {
            return Component.Serializer.fromJson(json, registryLookup);
        } catch (Exception exception) {
            BlockEntity.LOGGER.warn("Failed to parse custom name from string '{}', discarding", json, exception);
            return null;
        }
    }

    // CraftBukkit start - add method
    public InventoryHolder getOwner() {
        // Paper start
        return getOwner(true);
    }
    public InventoryHolder getOwner(boolean useSnapshot) {
        // Paper end
        if (this.level == null) return null;
        org.bukkit.block.Block block = this.level.getWorld().getBlockAt(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
        // if (block.getType() == org.bukkit.Material.AIR) return null; // Paper - actually get the tile entity if it still exists
        org.bukkit.block.BlockState state = block.getState(useSnapshot); // Paper
        if (state instanceof InventoryHolder) return (InventoryHolder) state;
        return null;
    }
    // CraftBukkit end

    // Paper start - Sanitize sent data
    public CompoundTag sanitizeSentNbt(CompoundTag tag) {
        tag.remove("PublicBukkitValues");

        return tag;
    }
    // Paper end - Sanitize sent data

    private static class ComponentHelper {

        public static final Codec<DataComponentMap> COMPONENTS_CODEC = DataComponentMap.CODEC.optionalFieldOf("components", DataComponentMap.EMPTY).codec();

        private ComponentHelper() {}
    }

    protected interface DataComponentInput {

        @Nullable
        <T> T get(DataComponentType<T> type);

        <T> T getOrDefault(DataComponentType<? extends T> type, T fallback);
    }
}
