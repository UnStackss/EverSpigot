package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.List;
// CraftBukkit end

public class BannerBlockEntity extends BlockEntity implements Nameable {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MAX_PATTERNS = 6;
    private static final String TAG_PATTERNS = "patterns";
    @Nullable
    public Component name; // Paper - public
    public DyeColor baseColor;
    private BannerPatternLayers patterns;

    public BannerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.BANNER, pos, state);
        this.patterns = BannerPatternLayers.EMPTY;
        this.baseColor = ((AbstractBannerBlock) state.getBlock()).getColor();
    }

    public BannerBlockEntity(BlockPos pos, BlockState state, DyeColor baseColor) {
        this(pos, state);
        this.baseColor = baseColor;
    }

    public void fromItem(ItemStack stack, DyeColor baseColor) {
        this.baseColor = baseColor;
        this.applyComponentsFromItemStack(stack);
    }

    @Override
    public Component getName() {
        return (Component) (this.name != null ? this.name : Component.translatable("block.minecraft.banner"));
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        if (!this.patterns.equals(BannerPatternLayers.EMPTY)) {
            nbt.put("patterns", (Tag) BannerPatternLayers.CODEC.encodeStart(registryLookup.createSerializationContext(NbtOps.INSTANCE), this.patterns).getOrThrow());
        }

        if (this.name != null) {
            nbt.putString("CustomName", Component.Serializer.toJson(this.name, registryLookup));
        }

    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        if (nbt.contains("CustomName", 8)) {
            this.name = parseCustomNameSafe(nbt.getString("CustomName"), registryLookup);
        }

        if (nbt.contains("patterns")) {
            BannerPatternLayers.CODEC.parse(registryLookup.createSerializationContext(NbtOps.INSTANCE), nbt.get("patterns")).resultOrPartial((s) -> {
                BannerBlockEntity.LOGGER.error("Failed to parse banner patterns: '{}'", s);
            }).ifPresent((bannerpatternlayers) -> {
                this.setPatterns(bannerpatternlayers); // CraftBukkit - apply limits
            });
        }

    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return this.saveWithoutMetadata(registryLookup);
    }

    public BannerPatternLayers getPatterns() {
        return this.patterns;
    }

    public ItemStack getItem() {
        ItemStack itemstack = new ItemStack(BannerBlock.byColor(this.baseColor));

        itemstack.applyComponents(this.collectComponents());
        return itemstack;
    }

    public DyeColor getBaseColor() {
        return this.baseColor;
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        this.setPatterns((BannerPatternLayers) components.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)); // CraftBukkit - apply limits
        this.name = (Component) components.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder componentMapBuilder) {
        super.collectImplicitComponents(componentMapBuilder);
        componentMapBuilder.set(DataComponents.BANNER_PATTERNS, this.patterns);
        componentMapBuilder.set(DataComponents.CUSTOM_NAME, this.name);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag nbt) {
        nbt.remove("patterns");
        nbt.remove("CustomName");
    }

    // CraftBukkit start
    public void setPatterns(BannerPatternLayers bannerpatternlayers) {
        if (bannerpatternlayers.layers().size() > 20) {
            bannerpatternlayers = new BannerPatternLayers(List.copyOf(bannerpatternlayers.layers().subList(0, 20)));
        }
        this.patterns = bannerpatternlayers;
    }
    // CraftBukkit end
}
