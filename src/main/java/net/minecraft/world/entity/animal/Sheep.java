package net.minecraft.world.entity.animal;

import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
// CraftBukkit start
import net.minecraft.world.item.Item;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.SheepRegrowWoolEvent;
// CraftBukkit end

public class Sheep extends Animal implements Shearable {

    private static final int EAT_ANIMATION_TICKS = 40;
    private static final EntityDataAccessor<Byte> DATA_WOOL_ID = SynchedEntityData.defineId(Sheep.class, EntityDataSerializers.BYTE);
    private static final Map<DyeColor, ItemLike> ITEM_BY_DYE = (Map) Util.make(Maps.newEnumMap(DyeColor.class), (enummap) -> {
        enummap.put(DyeColor.WHITE, Blocks.WHITE_WOOL);
        enummap.put(DyeColor.ORANGE, Blocks.ORANGE_WOOL);
        enummap.put(DyeColor.MAGENTA, Blocks.MAGENTA_WOOL);
        enummap.put(DyeColor.LIGHT_BLUE, Blocks.LIGHT_BLUE_WOOL);
        enummap.put(DyeColor.YELLOW, Blocks.YELLOW_WOOL);
        enummap.put(DyeColor.LIME, Blocks.LIME_WOOL);
        enummap.put(DyeColor.PINK, Blocks.PINK_WOOL);
        enummap.put(DyeColor.GRAY, Blocks.GRAY_WOOL);
        enummap.put(DyeColor.LIGHT_GRAY, Blocks.LIGHT_GRAY_WOOL);
        enummap.put(DyeColor.CYAN, Blocks.CYAN_WOOL);
        enummap.put(DyeColor.PURPLE, Blocks.PURPLE_WOOL);
        enummap.put(DyeColor.BLUE, Blocks.BLUE_WOOL);
        enummap.put(DyeColor.BROWN, Blocks.BROWN_WOOL);
        enummap.put(DyeColor.GREEN, Blocks.GREEN_WOOL);
        enummap.put(DyeColor.RED, Blocks.RED_WOOL);
        enummap.put(DyeColor.BLACK, Blocks.BLACK_WOOL);
    });
    private static final Map<DyeColor, Integer> COLOR_BY_DYE = Maps.newEnumMap((Map) Arrays.stream(DyeColor.values()).collect(Collectors.toMap((enumcolor) -> {
        return enumcolor;
    }, Sheep::createSheepColor)));
    private int eatAnimationTick;
    private EatBlockGoal eatBlockGoal;

    private static int createSheepColor(DyeColor color) {
        if (color == DyeColor.WHITE) {
            return -1644826;
        } else {
            int i = color.getTextureDiffuseColor();
            float f = 0.75F;

            return FastColor.ARGB32.color(255, Mth.floor((float) FastColor.ARGB32.red(i) * 0.75F), Mth.floor((float) FastColor.ARGB32.green(i) * 0.75F), Mth.floor((float) FastColor.ARGB32.blue(i) * 0.75F));
        }
    }

    public static int getColor(DyeColor dyeColor) {
        return (Integer) Sheep.COLOR_BY_DYE.get(dyeColor);
    }

    public Sheep(EntityType<? extends Sheep> type, Level world) {
        super(type, world);
    }

    @Override
    protected void registerGoals() {
        this.eatBlockGoal = new EatBlockGoal(this);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.1D, (itemstack) -> {
            return itemstack.is(ItemTags.SHEEP_FOOD);
        }, false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(5, this.eatBlockGoal);
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.SHEEP_FOOD);
    }

    @Override
    protected void customServerAiStep() {
        this.eatAnimationTick = this.eatBlockGoal.getEatAnimationTick();
        super.customServerAiStep();
    }

    @Override
    public void aiStep() {
        if (this.level().isClientSide) {
            this.eatAnimationTick = Math.max(0, this.eatAnimationTick - 1);
        }

        super.aiStep();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 8.0D).add(Attributes.MOVEMENT_SPEED, 0.23000000417232513D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Sheep.DATA_WOOL_ID, (byte) 0);
    }

    @Override
    public ResourceKey<LootTable> getDefaultLootTable() {
        if (this.isSheared()) {
            return this.getType().getDefaultLootTable();
        } else {
            ResourceKey resourcekey;

            switch (this.getColor()) {
                case WHITE:
                    resourcekey = BuiltInLootTables.SHEEP_WHITE;
                    break;
                case ORANGE:
                    resourcekey = BuiltInLootTables.SHEEP_ORANGE;
                    break;
                case MAGENTA:
                    resourcekey = BuiltInLootTables.SHEEP_MAGENTA;
                    break;
                case LIGHT_BLUE:
                    resourcekey = BuiltInLootTables.SHEEP_LIGHT_BLUE;
                    break;
                case YELLOW:
                    resourcekey = BuiltInLootTables.SHEEP_YELLOW;
                    break;
                case LIME:
                    resourcekey = BuiltInLootTables.SHEEP_LIME;
                    break;
                case PINK:
                    resourcekey = BuiltInLootTables.SHEEP_PINK;
                    break;
                case GRAY:
                    resourcekey = BuiltInLootTables.SHEEP_GRAY;
                    break;
                case LIGHT_GRAY:
                    resourcekey = BuiltInLootTables.SHEEP_LIGHT_GRAY;
                    break;
                case CYAN:
                    resourcekey = BuiltInLootTables.SHEEP_CYAN;
                    break;
                case PURPLE:
                    resourcekey = BuiltInLootTables.SHEEP_PURPLE;
                    break;
                case BLUE:
                    resourcekey = BuiltInLootTables.SHEEP_BLUE;
                    break;
                case BROWN:
                    resourcekey = BuiltInLootTables.SHEEP_BROWN;
                    break;
                case GREEN:
                    resourcekey = BuiltInLootTables.SHEEP_GREEN;
                    break;
                case RED:
                    resourcekey = BuiltInLootTables.SHEEP_RED;
                    break;
                case BLACK:
                    resourcekey = BuiltInLootTables.SHEEP_BLACK;
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return resourcekey;
        }
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 10) {
            this.eatAnimationTick = 40;
        } else {
            super.handleEntityEvent(status);
        }

    }

    public float getHeadEatPositionScale(float delta) {
        return this.eatAnimationTick <= 0 ? 0.0F : (this.eatAnimationTick >= 4 && this.eatAnimationTick <= 36 ? 1.0F : (this.eatAnimationTick < 4 ? ((float) this.eatAnimationTick - delta) / 4.0F : -((float) (this.eatAnimationTick - 40) - delta) / 4.0F));
    }

    public float getHeadEatAngleScale(float delta) {
        if (this.eatAnimationTick > 4 && this.eatAnimationTick <= 36) {
            float f1 = ((float) (this.eatAnimationTick - 4) - delta) / 32.0F;

            return 0.62831855F + 0.21991149F * Mth.sin(f1 * 28.7F);
        } else {
            return this.eatAnimationTick > 0 ? 0.62831855F : this.getXRot() * 0.017453292F;
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.SHEARS)) {
            if (!this.level().isClientSide && this.readyForShearing()) {
                // CraftBukkit start
                if (!CraftEventFactory.handlePlayerShearEntityEvent(player, this, itemstack, hand)) {
                    return InteractionResult.PASS;
                }
                // CraftBukkit end
                this.shear(SoundSource.PLAYERS);
                this.gameEvent(GameEvent.SHEAR, player);
                itemstack.hurtAndBreak(1, player, getSlotForHand(hand));
                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.CONSUME;
            }
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public void shear(SoundSource shearedSoundCategory) {
        this.level().playSound((Player) null, (Entity) this, SoundEvents.SHEEP_SHEAR, shearedSoundCategory, 1.0F, 1.0F);
        this.setSheared(true);
        int i = 1 + this.random.nextInt(3);

        for (int j = 0; j < i; ++j) {
            this.forceDrops = true; // CraftBukkit
            ItemEntity entityitem = this.spawnAtLocation((ItemLike) Sheep.ITEM_BY_DYE.get(this.getColor()), 1);
            this.forceDrops = false; // CraftBukkit

            if (entityitem != null) {
                entityitem.setDeltaMovement(entityitem.getDeltaMovement().add((double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.1F), (double) (this.random.nextFloat() * 0.05F), (double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.1F)));
            }
        }

    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && !this.isSheared() && !this.isBaby();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("Sheared", this.isSheared());
        nbt.putByte("Color", (byte) this.getColor().getId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setSheared(nbt.getBoolean("Sheared"));
        this.setColor(DyeColor.byId(nbt.getByte("Color")));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SHEEP_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SHEEP_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SHEEP_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SHEEP_STEP, 0.15F, 1.0F);
    }

    public DyeColor getColor() {
        return DyeColor.byId((Byte) this.entityData.get(Sheep.DATA_WOOL_ID) & 15);
    }

    public void setColor(DyeColor color) {
        byte b0 = (Byte) this.entityData.get(Sheep.DATA_WOOL_ID);

        this.entityData.set(Sheep.DATA_WOOL_ID, (byte) (b0 & 240 | color.getId() & 15));
    }

    public boolean isSheared() {
        return ((Byte) this.entityData.get(Sheep.DATA_WOOL_ID) & 16) != 0;
    }

    public void setSheared(boolean sheared) {
        byte b0 = (Byte) this.entityData.get(Sheep.DATA_WOOL_ID);

        if (sheared) {
            this.entityData.set(Sheep.DATA_WOOL_ID, (byte) (b0 | 16));
        } else {
            this.entityData.set(Sheep.DATA_WOOL_ID, (byte) (b0 & -17));
        }

    }

    public static DyeColor getRandomSheepColor(RandomSource random) {
        int i = random.nextInt(100);

        return i < 5 ? DyeColor.BLACK : (i < 10 ? DyeColor.GRAY : (i < 15 ? DyeColor.LIGHT_GRAY : (i < 18 ? DyeColor.BROWN : (random.nextInt(500) == 0 ? DyeColor.PINK : DyeColor.WHITE))));
    }

    @Nullable
    @Override
    public Sheep getBreedOffspring(ServerLevel world, AgeableMob entity) {
        Sheep entitysheep = (Sheep) EntityType.SHEEP.create(world);

        if (entitysheep != null) {
            entitysheep.setColor(this.getOffspringColor(this, (Sheep) entity));
        }

        return entitysheep;
    }

    @Override
    public void ate() {
        // CraftBukkit start
        SheepRegrowWoolEvent event = new SheepRegrowWoolEvent((org.bukkit.entity.Sheep) this.getBukkitEntity());
        this.level().getCraftServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) return;
        // CraftBukkit end
        super.ate();
        this.setSheared(false);
        if (this.isBaby()) {
            this.ageUp(60);
        }

    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        this.setColor(Sheep.getRandomSheepColor(world.getRandom()));
        return super.finalizeSpawn(world, difficulty, spawnReason, entityData);
    }

    private DyeColor getOffspringColor(Animal firstParent, Animal secondParent) {
        DyeColor enumcolor = ((Sheep) firstParent).getColor();
        DyeColor enumcolor1 = ((Sheep) secondParent).getColor();
        CraftingInput craftinginput = Sheep.makeCraftInput(enumcolor, enumcolor1);
        Optional<Item> optional = this.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftinginput, this.level()).map((recipeholder) -> { // CraftBukkit - decompile error
            return ((CraftingRecipe) recipeholder.value()).assemble(craftinginput, this.level().registryAccess());
        }).map(ItemStack::getItem);

        Objects.requireNonNull(DyeItem.class);
        optional = optional.filter(DyeItem.class::isInstance);
        Objects.requireNonNull(DyeItem.class);
        return (DyeColor) optional.map(DyeItem.class::cast).map(DyeItem::getDyeColor).orElseGet(() -> {
            return this.level().random.nextBoolean() ? enumcolor : enumcolor1;
        });
    }

    private static CraftingInput makeCraftInput(DyeColor firstColor, DyeColor secondColor) {
        return CraftingInput.of(2, 1, List.of(new ItemStack(DyeItem.byColor(firstColor)), new ItemStack(DyeItem.byColor(secondColor))));
    }
}
