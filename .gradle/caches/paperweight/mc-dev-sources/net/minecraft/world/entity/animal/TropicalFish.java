package net.minecraft.world.entity.animal;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;

public class TropicalFish extends AbstractSchoolingFish implements VariantHolder<TropicalFish.Pattern> {
    public static final String BUCKET_VARIANT_TAG = "BucketVariantTag";
    private static final EntityDataAccessor<Integer> DATA_ID_TYPE_VARIANT = SynchedEntityData.defineId(TropicalFish.class, EntityDataSerializers.INT);
    public static final List<TropicalFish.Variant> COMMON_VARIANTS = List.of(
        new TropicalFish.Variant(TropicalFish.Pattern.STRIPEY, DyeColor.ORANGE, DyeColor.GRAY),
        new TropicalFish.Variant(TropicalFish.Pattern.FLOPPER, DyeColor.GRAY, DyeColor.GRAY),
        new TropicalFish.Variant(TropicalFish.Pattern.FLOPPER, DyeColor.GRAY, DyeColor.BLUE),
        new TropicalFish.Variant(TropicalFish.Pattern.CLAYFISH, DyeColor.WHITE, DyeColor.GRAY),
        new TropicalFish.Variant(TropicalFish.Pattern.SUNSTREAK, DyeColor.BLUE, DyeColor.GRAY),
        new TropicalFish.Variant(TropicalFish.Pattern.KOB, DyeColor.ORANGE, DyeColor.WHITE),
        new TropicalFish.Variant(TropicalFish.Pattern.SPOTTY, DyeColor.PINK, DyeColor.LIGHT_BLUE),
        new TropicalFish.Variant(TropicalFish.Pattern.BLOCKFISH, DyeColor.PURPLE, DyeColor.YELLOW),
        new TropicalFish.Variant(TropicalFish.Pattern.CLAYFISH, DyeColor.WHITE, DyeColor.RED),
        new TropicalFish.Variant(TropicalFish.Pattern.SPOTTY, DyeColor.WHITE, DyeColor.YELLOW),
        new TropicalFish.Variant(TropicalFish.Pattern.GLITTER, DyeColor.WHITE, DyeColor.GRAY),
        new TropicalFish.Variant(TropicalFish.Pattern.CLAYFISH, DyeColor.WHITE, DyeColor.ORANGE),
        new TropicalFish.Variant(TropicalFish.Pattern.DASHER, DyeColor.CYAN, DyeColor.PINK),
        new TropicalFish.Variant(TropicalFish.Pattern.BRINELY, DyeColor.LIME, DyeColor.LIGHT_BLUE),
        new TropicalFish.Variant(TropicalFish.Pattern.BETTY, DyeColor.RED, DyeColor.WHITE),
        new TropicalFish.Variant(TropicalFish.Pattern.SNOOPER, DyeColor.GRAY, DyeColor.RED),
        new TropicalFish.Variant(TropicalFish.Pattern.BLOCKFISH, DyeColor.RED, DyeColor.WHITE),
        new TropicalFish.Variant(TropicalFish.Pattern.FLOPPER, DyeColor.WHITE, DyeColor.YELLOW),
        new TropicalFish.Variant(TropicalFish.Pattern.KOB, DyeColor.RED, DyeColor.WHITE),
        new TropicalFish.Variant(TropicalFish.Pattern.SUNSTREAK, DyeColor.GRAY, DyeColor.WHITE),
        new TropicalFish.Variant(TropicalFish.Pattern.DASHER, DyeColor.CYAN, DyeColor.YELLOW),
        new TropicalFish.Variant(TropicalFish.Pattern.FLOPPER, DyeColor.YELLOW, DyeColor.YELLOW)
    );
    private boolean isSchool = true;

    public TropicalFish(EntityType<? extends TropicalFish> type, Level world) {
        super(type, world);
    }

    public static String getPredefinedName(int variant) {
        return "entity.minecraft.tropical_fish.predefined." + variant;
    }

    static int packVariant(TropicalFish.Pattern variety, DyeColor baseColor, DyeColor patternColor) {
        return variety.getPackedId() & 65535 | (baseColor.getId() & 0xFF) << 16 | (patternColor.getId() & 0xFF) << 24;
    }

    public static DyeColor getBaseColor(int variant) {
        return DyeColor.byId(variant >> 16 & 0xFF);
    }

    public static DyeColor getPatternColor(int variant) {
        return DyeColor.byId(variant >> 24 & 0xFF);
    }

    public static TropicalFish.Pattern getPattern(int variant) {
        return TropicalFish.Pattern.byId(variant & 65535);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_TYPE_VARIANT, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Variant", this.getPackedVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setPackedVariant(nbt.getInt("Variant"));
    }

    public void setPackedVariant(int variant) {
        this.entityData.set(DATA_ID_TYPE_VARIANT, variant);
    }

    @Override
    public boolean isMaxGroupSizeReached(int count) {
        return !this.isSchool;
    }

    public int getPackedVariant() {
        return this.entityData.get(DATA_ID_TYPE_VARIANT);
    }

    public DyeColor getBaseColor() {
        return getBaseColor(this.getPackedVariant());
    }

    public DyeColor getPatternColor() {
        return getPatternColor(this.getPackedVariant());
    }

    @Override
    public TropicalFish.Pattern getVariant() {
        return getPattern(this.getPackedVariant());
    }

    @Override
    public void setVariant(TropicalFish.Pattern variant) {
        int i = this.getPackedVariant();
        DyeColor dyeColor = getBaseColor(i);
        DyeColor dyeColor2 = getPatternColor(i);
        this.setPackedVariant(packVariant(variant, dyeColor, dyeColor2));
    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        super.saveToBucketTag(stack);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, compoundTag -> compoundTag.putInt("BucketVariantTag", this.getPackedVariant()));
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.TROPICAL_FISH_BUCKET);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.TROPICAL_FISH_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.TROPICAL_FISH_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.TROPICAL_FISH_HURT;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.TROPICAL_FISH_FLOP;
    }

    @Override
    public void loadFromBucketTag(CompoundTag nbt) {
        super.loadFromBucketTag(nbt);
        if (nbt.contains("BucketVariantTag", 3)) {
            this.setPackedVariant(nbt.getInt("BucketVariantTag"));
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        entityData = super.finalizeSpawn(world, difficulty, spawnReason, entityData);
        RandomSource randomSource = world.getRandom();
        TropicalFish.Variant variant;
        if (entityData instanceof TropicalFish.TropicalFishGroupData tropicalFishGroupData) {
            variant = tropicalFishGroupData.variant;
        } else if ((double)randomSource.nextFloat() < 0.9) {
            variant = Util.getRandom(COMMON_VARIANTS, randomSource);
            entityData = new TropicalFish.TropicalFishGroupData(this, variant);
        } else {
            this.isSchool = false;
            TropicalFish.Pattern[] patterns = TropicalFish.Pattern.values();
            DyeColor[] dyeColors = DyeColor.values();
            TropicalFish.Pattern pattern = Util.getRandom(patterns, randomSource);
            DyeColor dyeColor = Util.getRandom(dyeColors, randomSource);
            DyeColor dyeColor2 = Util.getRandom(dyeColors, randomSource);
            variant = new TropicalFish.Variant(pattern, dyeColor, dyeColor2);
        }

        this.setPackedVariant(variant.getPackedId());
        return entityData;
    }

    public static boolean checkTropicalFishSpawnRules(
        EntityType<TropicalFish> type, LevelAccessor world, MobSpawnType reason, BlockPos pos, RandomSource random
    ) {
        return world.getFluidState(pos.below()).is(FluidTags.WATER)
            && world.getBlockState(pos.above()).is(Blocks.WATER)
            && (
                world.getBiome(pos).is(BiomeTags.ALLOWS_TROPICAL_FISH_SPAWNS_AT_ANY_HEIGHT)
                    || WaterAnimal.checkSurfaceWaterAnimalSpawnRules(type, world, reason, pos, random)
            );
    }

    public static enum Base {
        SMALL(0),
        LARGE(1);

        final int id;

        private Base(final int id) {
            this.id = id;
        }
    }

    public static enum Pattern implements StringRepresentable {
        KOB("kob", TropicalFish.Base.SMALL, 0),
        SUNSTREAK("sunstreak", TropicalFish.Base.SMALL, 1),
        SNOOPER("snooper", TropicalFish.Base.SMALL, 2),
        DASHER("dasher", TropicalFish.Base.SMALL, 3),
        BRINELY("brinely", TropicalFish.Base.SMALL, 4),
        SPOTTY("spotty", TropicalFish.Base.SMALL, 5),
        FLOPPER("flopper", TropicalFish.Base.LARGE, 0),
        STRIPEY("stripey", TropicalFish.Base.LARGE, 1),
        GLITTER("glitter", TropicalFish.Base.LARGE, 2),
        BLOCKFISH("blockfish", TropicalFish.Base.LARGE, 3),
        BETTY("betty", TropicalFish.Base.LARGE, 4),
        CLAYFISH("clayfish", TropicalFish.Base.LARGE, 5);

        public static final Codec<TropicalFish.Pattern> CODEC = StringRepresentable.fromEnum(TropicalFish.Pattern::values);
        private static final IntFunction<TropicalFish.Pattern> BY_ID = ByIdMap.sparse(TropicalFish.Pattern::getPackedId, values(), KOB);
        private final String name;
        private final Component displayName;
        private final TropicalFish.Base base;
        private final int packedId;

        private Pattern(final String name, final TropicalFish.Base size, final int id) {
            this.name = name;
            this.base = size;
            this.packedId = size.id | id << 8;
            this.displayName = Component.translatable("entity.minecraft.tropical_fish.type." + this.name);
        }

        public static TropicalFish.Pattern byId(int id) {
            return BY_ID.apply(id);
        }

        public TropicalFish.Base base() {
            return this.base;
        }

        public int getPackedId() {
            return this.packedId;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public Component displayName() {
            return this.displayName;
        }
    }

    static class TropicalFishGroupData extends AbstractSchoolingFish.SchoolSpawnGroupData {
        final TropicalFish.Variant variant;

        TropicalFishGroupData(TropicalFish leader, TropicalFish.Variant variant) {
            super(leader);
            this.variant = variant;
        }
    }

    public static record Variant(TropicalFish.Pattern pattern, DyeColor baseColor, DyeColor patternColor) {
        public static final Codec<TropicalFish.Variant> CODEC = Codec.INT.xmap(TropicalFish.Variant::new, TropicalFish.Variant::getPackedId);

        public Variant(int id) {
            this(TropicalFish.getPattern(id), TropicalFish.getBaseColor(id), TropicalFish.getPatternColor(id));
        }

        public int getPackedId() {
            return TropicalFish.packVariant(this.pattern, this.baseColor, this.patternColor);
        }
    }
}
