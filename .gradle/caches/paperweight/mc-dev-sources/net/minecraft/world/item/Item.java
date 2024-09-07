package net.minecraft.world.item;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class Item implements FeatureElement, ItemLike {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Map<Block, Item> BY_BLOCK = Maps.newHashMap();
    public static final ResourceLocation BASE_ATTACK_DAMAGE_ID = ResourceLocation.withDefaultNamespace("base_attack_damage");
    public static final ResourceLocation BASE_ATTACK_SPEED_ID = ResourceLocation.withDefaultNamespace("base_attack_speed");
    public static final int DEFAULT_MAX_STACK_SIZE = 64;
    public static final int ABSOLUTE_MAX_STACK_SIZE = 99;
    public static final int MAX_BAR_WIDTH = 13;
    private final Holder.Reference<Item> builtInRegistryHolder = BuiltInRegistries.ITEM.createIntrusiveHolder(this);
    private final DataComponentMap components;
    @Nullable
    private final Item craftingRemainingItem;
    @Nullable
    private String descriptionId;
    private final FeatureFlagSet requiredFeatures;

    public static int getId(Item item) {
        return item == null ? 0 : BuiltInRegistries.ITEM.getId(item);
    }

    public static Item byId(int id) {
        return BuiltInRegistries.ITEM.byId(id);
    }

    @Deprecated
    public static Item byBlock(Block block) {
        return BY_BLOCK.getOrDefault(block, Items.AIR);
    }

    public Item(Item.Properties settings) {
        this.components = settings.buildAndValidateComponents();
        this.craftingRemainingItem = settings.craftingRemainingItem;
        this.requiredFeatures = settings.requiredFeatures;
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            String string = this.getClass().getSimpleName();
            if (!string.endsWith("Item")) {
                LOGGER.error("Item classes should end with Item and {} doesn't.", string);
            }
        }
    }

    @Deprecated
    public Holder.Reference<Item> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    public DataComponentMap components() {
        return this.components;
    }

    public int getDefaultMaxStackSize() {
        return this.components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
    }

    public void onDestroyed(ItemEntity entity) {
    }

    public void verifyComponentsAfterLoad(ItemStack stack) {
    }

    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player miner) {
        return true;
    }

    @Override
    public Item asItem() {
        return this;
    }

    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    public float getDestroySpeed(ItemStack stack, BlockState state) {
        Tool tool = stack.get(DataComponents.TOOL);
        return tool != null ? tool.getMiningSpeed(state) : 1.0F;
    }

    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        FoodProperties foodProperties = itemStack.get(DataComponents.FOOD);
        if (foodProperties != null) {
            if (user.canEat(foodProperties.canAlwaysEat())) {
                user.startUsingItem(hand);
                return InteractionResultHolder.consume(itemStack);
            } else {
                return InteractionResultHolder.fail(itemStack);
            }
        } else {
            return InteractionResultHolder.pass(user.getItemInHand(hand));
        }
    }

    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        FoodProperties foodProperties = stack.get(DataComponents.FOOD);
        return foodProperties != null ? user.eat(world, stack, foodProperties) : stack;
    }

    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamaged();
    }

    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F - (float)stack.getDamageValue() * 13.0F / (float)stack.getMaxDamage()), 0, 13);
    }

    public int getBarColor(ItemStack stack) {
        int i = stack.getMaxDamage();
        float f = Math.max(0.0F, ((float)i - (float)stack.getDamageValue()) / (float)i);
        return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
    }

    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction clickType, Player player) {
        return false;
    }

    public boolean overrideOtherStackedOnMe(
        ItemStack stack, ItemStack otherStack, Slot slot, ClickAction clickType, Player player, SlotAccess cursorStackReference
    ) {
        return false;
    }

    public float getAttackDamageBonus(Entity target, float baseAttackDamage, DamageSource damageSource) {
        return 0.0F;
    }

    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }

    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    }

    public boolean mineBlock(ItemStack stack, Level world, BlockState state, BlockPos pos, LivingEntity miner) {
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null) {
            return false;
        } else {
            if (!world.isClientSide && state.getDestroySpeed(world, pos) != 0.0F && tool.damagePerBlock() > 0) {
                stack.hurtAndBreak(tool.damagePerBlock(), miner, EquipmentSlot.MAINHAND);
            }

            return true;
        }
    }

    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        Tool tool = stack.get(DataComponents.TOOL);
        return tool != null && tool.isCorrectForDrops(state);
    }

    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public Component getDescription() {
        return Component.translatable(this.getDescriptionId());
    }

    @Override
    public String toString() {
        return BuiltInRegistries.ITEM.wrapAsHolder(this).getRegisteredName();
    }

    protected String getOrCreateDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("item", BuiltInRegistries.ITEM.getKey(this));
        }

        return this.descriptionId;
    }

    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    public String getDescriptionId(ItemStack stack) {
        return this.getDescriptionId();
    }

    @Nullable
    public final Item getCraftingRemainingItem() {
        return this.craftingRemainingItem;
    }

    public boolean hasCraftingRemainingItem() {
        return this.craftingRemainingItem != null;
    }

    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
    }

    public void onCraftedBy(ItemStack stack, Level world, Player player) {
        this.onCraftedPostProcess(stack, world);
    }

    public void onCraftedPostProcess(ItemStack stack, Level world) {
    }

    public boolean isComplex() {
        return false;
    }

    public UseAnim getUseAnimation(ItemStack stack) {
        return stack.has(DataComponents.FOOD) ? UseAnim.EAT : UseAnim.NONE;
    }

    public int getUseDuration(ItemStack stack, LivingEntity user) {
        FoodProperties foodProperties = stack.get(DataComponents.FOOD);
        return foodProperties != null ? foodProperties.eatDurationTicks() : 0;
    }

    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
    }

    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
    }

    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.empty();
    }

    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack));
    }

    public boolean isFoil(ItemStack stack) {
        return stack.isEnchanted();
    }

    public boolean isEnchantable(ItemStack stack) {
        return stack.getMaxStackSize() == 1 && stack.has(DataComponents.MAX_DAMAGE);
    }

    protected static BlockHitResult getPlayerPOVHitResult(Level world, Player player, ClipContext.Fluid fluidHandling) {
        Vec3 vec3 = player.getEyePosition();
        Vec3 vec32 = vec3.add(player.calculateViewVector(player.getXRot(), player.getYRot()).scale(player.blockInteractionRange()));
        return world.clip(new ClipContext(vec3, vec32, ClipContext.Block.OUTLINE, fluidHandling, player));
    }

    public int getEnchantmentValue() {
        return 0;
    }

    public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) {
        return false;
    }

    @Deprecated
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return ItemAttributeModifiers.EMPTY;
    }

    public boolean useOnRelease(ItemStack stack) {
        return false;
    }

    public ItemStack getDefaultInstance() {
        return new ItemStack(this);
    }

    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_EAT;
    }

    public SoundEvent getBreakingSound() {
        return SoundEvents.ITEM_BREAK;
    }

    public boolean canFitInsideContainerItems() {
        return true;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    public static class Properties {
        private static final Interner<DataComponentMap> COMPONENT_INTERNER = Interners.newStrongInterner();
        @Nullable
        private DataComponentMap.Builder components;
        @Nullable
        Item craftingRemainingItem;
        FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;

        public Item.Properties food(FoodProperties foodComponent) {
            return this.component(DataComponents.FOOD, foodComponent);
        }

        public Item.Properties stacksTo(int maxCount) {
            return this.component(DataComponents.MAX_STACK_SIZE, maxCount);
        }

        public Item.Properties durability(int maxDamage) {
            this.component(DataComponents.MAX_DAMAGE, maxDamage);
            this.component(DataComponents.MAX_STACK_SIZE, 1);
            this.component(DataComponents.DAMAGE, 0);
            return this;
        }

        public Item.Properties craftRemainder(Item recipeRemainder) {
            this.craftingRemainingItem = recipeRemainder;
            return this;
        }

        public Item.Properties rarity(Rarity rarity) {
            return this.component(DataComponents.RARITY, rarity);
        }

        public Item.Properties fireResistant() {
            return this.component(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
        }

        public Item.Properties jukeboxPlayable(ResourceKey<JukeboxSong> songKey) {
            return this.component(DataComponents.JUKEBOX_PLAYABLE, new JukeboxPlayable(new EitherHolder<>(songKey), true));
        }

        public Item.Properties requiredFeatures(FeatureFlag... features) {
            this.requiredFeatures = FeatureFlags.REGISTRY.subset(features);
            return this;
        }

        public <T> Item.Properties component(DataComponentType<T> type, T value) {
            if (this.components == null) {
                this.components = DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS);
            }

            this.components.set(type, value);
            return this;
        }

        public Item.Properties attributes(ItemAttributeModifiers attributeModifiersComponent) {
            return this.component(DataComponents.ATTRIBUTE_MODIFIERS, attributeModifiersComponent);
        }

        DataComponentMap buildAndValidateComponents() {
            DataComponentMap dataComponentMap = this.buildComponents();
            if (dataComponentMap.has(DataComponents.DAMAGE) && dataComponentMap.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
                throw new IllegalStateException("Item cannot have both durability and be stackable");
            } else {
                return dataComponentMap;
            }
        }

        private DataComponentMap buildComponents() {
            return this.components == null ? DataComponents.COMMON_ITEM_COMPONENTS : COMPONENT_INTERNER.intern(this.components.build());
        }
    }

    public interface TooltipContext {
        Item.TooltipContext EMPTY = new Item.TooltipContext() {
            @Nullable
            @Override
            public HolderLookup.Provider registries() {
                return null;
            }

            @Override
            public float tickRate() {
                return 20.0F;
            }

            @Nullable
            @Override
            public MapItemSavedData mapData(MapId mapIdComponent) {
                return null;
            }
        };

        @Nullable
        HolderLookup.Provider registries();

        float tickRate();

        @Nullable
        MapItemSavedData mapData(MapId mapIdComponent);

        static Item.TooltipContext of(@Nullable Level world) {
            return world == null ? EMPTY : new Item.TooltipContext() {
                @Override
                public HolderLookup.Provider registries() {
                    return world.registryAccess();
                }

                @Override
                public float tickRate() {
                    return world.tickRateManager().tickrate();
                }

                @Override
                public MapItemSavedData mapData(MapId mapIdComponent) {
                    return world.getMapData(mapIdComponent);
                }
            };
        }

        static Item.TooltipContext of(HolderLookup.Provider registryLookup) {
            return new Item.TooltipContext() {
                @Override
                public HolderLookup.Provider registries() {
                    return registryLookup;
                }

                @Override
                public float tickRate() {
                    return 20.0F;
                }

                @Nullable
                @Override
                public MapItemSavedData mapData(MapId mapIdComponent) {
                    return null;
                }
            };
        }
    }
}
