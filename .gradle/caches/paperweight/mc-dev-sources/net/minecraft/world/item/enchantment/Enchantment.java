package net.minecraft.world.item.enchantment;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.effects.DamageImmunity;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;

public record Enchantment(Component description, Enchantment.EnchantmentDefinition definition, HolderSet<Enchantment> exclusiveSet, DataComponentMap effects) {
    public static final int MAX_LEVEL = 255;
    public static final Codec<Enchantment> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ComponentSerialization.CODEC.fieldOf("description").forGetter(Enchantment::description),
                    Enchantment.EnchantmentDefinition.CODEC.forGetter(Enchantment::definition),
                    RegistryCodecs.homogeneousList(Registries.ENCHANTMENT)
                        .optionalFieldOf("exclusive_set", HolderSet.direct())
                        .forGetter(Enchantment::exclusiveSet),
                    EnchantmentEffectComponents.CODEC.optionalFieldOf("effects", DataComponentMap.EMPTY).forGetter(Enchantment::effects)
                )
                .apply(instance, Enchantment::new)
    );
    public static final Codec<Holder<Enchantment>> CODEC = RegistryFixedCodec.create(Registries.ENCHANTMENT);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Enchantment>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT);

    public static Enchantment.Cost constantCost(int base) {
        return new Enchantment.Cost(base, 0);
    }

    public static Enchantment.Cost dynamicCost(int base, int perLevel) {
        return new Enchantment.Cost(base, perLevel);
    }

    public static Enchantment.EnchantmentDefinition definition(
        HolderSet<Item> supportedItems,
        HolderSet<Item> primaryItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        EquipmentSlotGroup... slots
    ) {
        return new Enchantment.EnchantmentDefinition(supportedItems, Optional.of(primaryItems), weight, maxLevel, minCost, maxCost, anvilCost, List.of(slots));
    }

    public static Enchantment.EnchantmentDefinition definition(
        HolderSet<Item> supportedItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        EquipmentSlotGroup... slots
    ) {
        return new Enchantment.EnchantmentDefinition(supportedItems, Optional.empty(), weight, maxLevel, minCost, maxCost, anvilCost, List.of(slots));
    }

    public Map<EquipmentSlot, ItemStack> getSlotItems(LivingEntity entity) {
        Map<EquipmentSlot, ItemStack> map = Maps.newEnumMap(EquipmentSlot.class);

        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            if (this.matchingSlot(equipmentSlot)) {
                ItemStack itemStack = entity.getItemBySlot(equipmentSlot);
                if (!itemStack.isEmpty()) {
                    map.put(equipmentSlot, itemStack);
                }
            }
        }

        return map;
    }

    public HolderSet<Item> getSupportedItems() {
        return this.definition.supportedItems();
    }

    public boolean matchingSlot(EquipmentSlot slot) {
        return this.definition.slots().stream().anyMatch(slotx -> slotx.test(slot));
    }

    public boolean isPrimaryItem(ItemStack stack) {
        return this.isSupportedItem(stack) && (this.definition.primaryItems.isEmpty() || stack.is(this.definition.primaryItems.get()));
    }

    public boolean isSupportedItem(ItemStack stack) {
        return stack.is(this.definition.supportedItems);
    }

    public int getWeight() {
        return this.definition.weight();
    }

    public int getAnvilCost() {
        return this.definition.anvilCost();
    }

    public int getMinLevel() {
        return 1;
    }

    public int getMaxLevel() {
        return this.definition.maxLevel();
    }

    public int getMinCost(int level) {
        return this.definition.minCost().calculate(level);
    }

    public int getMaxCost(int level) {
        return this.definition.maxCost().calculate(level);
    }

    @Override
    public String toString() {
        return "Enchantment " + this.description.getString();
    }

    public static boolean areCompatible(Holder<Enchantment> first, Holder<Enchantment> second) {
        return !first.equals(second) && !first.value().exclusiveSet.contains(second) && !second.value().exclusiveSet.contains(first);
    }

    public static Component getFullname(Holder<Enchantment> enchantment, int level) {
        MutableComponent mutableComponent = enchantment.value().description.copy();
        if (enchantment.is(EnchantmentTags.CURSE)) {
            ComponentUtils.mergeStyles(mutableComponent, Style.EMPTY.withColor(ChatFormatting.RED));
        } else {
            ComponentUtils.mergeStyles(mutableComponent, Style.EMPTY.withColor(ChatFormatting.GRAY));
        }

        if (level != 1 || enchantment.value().getMaxLevel() != 1) {
            mutableComponent.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + level));
        }

        return mutableComponent;
    }

    public boolean canEnchant(ItemStack stack) {
        return this.definition.supportedItems().contains(stack.getItemHolder());
    }

    public <T> List<T> getEffects(DataComponentType<List<T>> type) {
        return this.effects.getOrDefault(type, List.of());
    }

    public boolean isImmuneToDamage(ServerLevel world, int level, Entity user, DamageSource damageSource) {
        LootContext lootContext = damageContext(world, level, user, damageSource);

        for (ConditionalEffect<DamageImmunity> conditionalEffect : this.getEffects(EnchantmentEffectComponents.DAMAGE_IMMUNITY)) {
            if (conditionalEffect.matches(lootContext)) {
                return true;
            }
        }

        return false;
    }

    public void modifyDamageProtection(ServerLevel world, int level, ItemStack stack, Entity user, DamageSource damageSource, MutableFloat damageProtection) {
        LootContext lootContext = damageContext(world, level, user, damageSource);

        for (ConditionalEffect<EnchantmentValueEffect> conditionalEffect : this.getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION)) {
            if (conditionalEffect.matches(lootContext)) {
                damageProtection.setValue(conditionalEffect.effect().process(level, user.getRandom(), damageProtection.floatValue()));
            }
        }
    }

    public void modifyDurabilityChange(ServerLevel world, int level, ItemStack stack, MutableFloat itemDamage) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.ITEM_DAMAGE, world, level, stack, itemDamage);
    }

    public void modifyAmmoCount(ServerLevel world, int level, ItemStack projectileStack, MutableFloat ammoUse) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.AMMO_USE, world, level, projectileStack, ammoUse);
    }

    public void modifyPiercingCount(ServerLevel world, int level, ItemStack stack, MutableFloat projectilePiercing) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.PROJECTILE_PIERCING, world, level, stack, projectilePiercing);
    }

    public void modifyBlockExperience(ServerLevel world, int level, ItemStack stack, MutableFloat blockExperience) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.BLOCK_EXPERIENCE, world, level, stack, blockExperience);
    }

    public void modifyMobExperience(ServerLevel world, int level, ItemStack stack, Entity user, MutableFloat mobExperience) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.MOB_EXPERIENCE, world, level, stack, user, mobExperience);
    }

    public void modifyDurabilityToRepairFromXp(ServerLevel world, int level, ItemStack stack, MutableFloat repairWithXp) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.REPAIR_WITH_XP, world, level, stack, repairWithXp);
    }

    public void modifyTridentReturnToOwnerAcceleration(ServerLevel world, int level, ItemStack stack, Entity user, MutableFloat tridentReturnAcceleration) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION, world, level, stack, user, tridentReturnAcceleration);
    }

    public void modifyTridentSpinAttackStrength(RandomSource random, int level, MutableFloat tridentSpinAttackStrength) {
        this.modifyUnfilteredValue(EnchantmentEffectComponents.TRIDENT_SPIN_ATTACK_STRENGTH, random, level, tridentSpinAttackStrength);
    }

    public void modifyFishingTimeReduction(ServerLevel world, int level, ItemStack stack, Entity user, MutableFloat fishingTimeReduction) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.FISHING_TIME_REDUCTION, world, level, stack, user, fishingTimeReduction);
    }

    public void modifyFishingLuckBonus(ServerLevel world, int level, ItemStack stack, Entity user, MutableFloat fishingLuckBonus) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.FISHING_LUCK_BONUS, world, level, stack, user, fishingLuckBonus);
    }

    public void modifyDamage(ServerLevel world, int level, ItemStack stack, Entity user, DamageSource damageSource, MutableFloat damage) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.DAMAGE, world, level, stack, user, damageSource, damage);
    }

    public void modifyFallBasedDamage(
        ServerLevel world, int level, ItemStack stack, Entity user, DamageSource damageSource, MutableFloat smashDamagePerFallenBlock
    ) {
        this.modifyDamageFilteredValue(
            EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK, world, level, stack, user, damageSource, smashDamagePerFallenBlock
        );
    }

    public void modifyKnockback(ServerLevel world, int level, ItemStack stack, Entity user, DamageSource damageSource, MutableFloat knockback) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.KNOCKBACK, world, level, stack, user, damageSource, knockback);
    }

    public void modifyArmorEffectivness(ServerLevel world, int level, ItemStack stack, Entity user, DamageSource damageSource, MutableFloat armorEffectiveness) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.ARMOR_EFFECTIVENESS, world, level, stack, user, damageSource, armorEffectiveness);
    }

    public static void doPostAttack(
        TargetedConditionalEffect<EnchantmentEntityEffect> effect,
        ServerLevel world,
        int level,
        EnchantedItemInUse context,
        Entity user,
        DamageSource damageSource
    ) {
        if (effect.matches(damageContext(world, level, user, damageSource))) {
            Entity entity = switch (effect.affected()) {
                case ATTACKER -> damageSource.getEntity();
                case DAMAGING_ENTITY -> damageSource.getDirectEntity();
                case VICTIM -> user;
            };
            if (entity != null) {
                effect.effect().apply(world, level, context, entity, entity.position());
            }
        }
    }

    public void doPostAttack(ServerLevel world, int level, EnchantedItemInUse context, EnchantmentTarget target, Entity user, DamageSource damageSource) {
        for (TargetedConditionalEffect<EnchantmentEntityEffect> targetedConditionalEffect : this.getEffects(EnchantmentEffectComponents.POST_ATTACK)) {
            if (target == targetedConditionalEffect.enchanted()) {
                doPostAttack(targetedConditionalEffect, world, level, context, user, damageSource);
            }
        }
    }

    public void modifyProjectileCount(ServerLevel world, int level, ItemStack stack, Entity user, MutableFloat projectileCount) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.PROJECTILE_COUNT, world, level, stack, user, projectileCount);
    }

    public void modifyProjectileSpread(ServerLevel world, int level, ItemStack stack, Entity user, MutableFloat projectileSpread) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.PROJECTILE_SPREAD, world, level, stack, user, projectileSpread);
    }

    public void modifyCrossbowChargeTime(RandomSource random, int level, MutableFloat crossbowChargeTime) {
        this.modifyUnfilteredValue(EnchantmentEffectComponents.CROSSBOW_CHARGE_TIME, random, level, crossbowChargeTime);
    }

    public void modifyUnfilteredValue(DataComponentType<EnchantmentValueEffect> type, RandomSource random, int level, MutableFloat value) {
        EnchantmentValueEffect enchantmentValueEffect = this.effects.get(type);
        if (enchantmentValueEffect != null) {
            value.setValue(enchantmentValueEffect.process(level, random, value.floatValue()));
        }
    }

    public void tick(ServerLevel world, int level, EnchantedItemInUse context, Entity user) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.TICK),
            entityContext(world, level, user, user.position()),
            effect -> effect.apply(world, level, context, user, user.position())
        );
    }

    public void onProjectileSpawned(ServerLevel world, int level, EnchantedItemInUse context, Entity user) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED),
            entityContext(world, level, user, user.position()),
            effect -> effect.apply(world, level, context, user, user.position())
        );
    }

    public void onHitBlock(ServerLevel world, int level, EnchantedItemInUse context, Entity enchantedEntity, Vec3 pos, BlockState state) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.HIT_BLOCK),
            blockHitContext(world, level, enchantedEntity, pos, state),
            effect -> effect.apply(world, level, context, enchantedEntity, pos)
        );
    }

    private void modifyItemFilteredCount(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> type, ServerLevel world, int level, ItemStack stack, MutableFloat value
    ) {
        applyEffects(
            this.getEffects(type), itemContext(world, level, stack), effect -> value.setValue(effect.process(level, world.getRandom(), value.getValue()))
        );
    }

    private void modifyEntityFilteredValue(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> type, ServerLevel world, int level, ItemStack stack, Entity user, MutableFloat value
    ) {
        applyEffects(
            this.getEffects(type),
            entityContext(world, level, user, user.position()),
            effect -> value.setValue(effect.process(level, user.getRandom(), value.floatValue()))
        );
    }

    private void modifyDamageFilteredValue(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> type,
        ServerLevel world,
        int level,
        ItemStack stack,
        Entity user,
        DamageSource damageSource,
        MutableFloat value
    ) {
        applyEffects(
            this.getEffects(type),
            damageContext(world, level, user, damageSource),
            effect -> value.setValue(effect.process(level, user.getRandom(), value.floatValue()))
        );
    }

    public static LootContext damageContext(ServerLevel world, int level, Entity entity, DamageSource damageSource) {
        LootParams lootParams = new LootParams.Builder(world)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, level)
            .withParameter(LootContextParams.ORIGIN, entity.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity())
            .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, damageSource.getDirectEntity())
            .create(LootContextParamSets.ENCHANTED_DAMAGE);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    private static LootContext itemContext(ServerLevel world, int level, ItemStack stack) {
        LootParams lootParams = new LootParams.Builder(world)
            .withParameter(LootContextParams.TOOL, stack)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, level)
            .create(LootContextParamSets.ENCHANTED_ITEM);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    private static LootContext locationContext(ServerLevel world, int level, Entity entity, boolean enchantmentActive) {
        LootParams lootParams = new LootParams.Builder(world)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, level)
            .withParameter(LootContextParams.ORIGIN, entity.position())
            .withParameter(LootContextParams.ENCHANTMENT_ACTIVE, enchantmentActive)
            .create(LootContextParamSets.ENCHANTED_LOCATION);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    private static LootContext entityContext(ServerLevel world, int level, Entity entity, Vec3 pos) {
        LootParams lootParams = new LootParams.Builder(world)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, level)
            .withParameter(LootContextParams.ORIGIN, pos)
            .create(LootContextParamSets.ENCHANTED_ENTITY);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    private static LootContext blockHitContext(ServerLevel world, int level, Entity entity, Vec3 pos, BlockState state) {
        LootParams lootParams = new LootParams.Builder(world)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, level)
            .withParameter(LootContextParams.ORIGIN, pos)
            .withParameter(LootContextParams.BLOCK_STATE, state)
            .create(LootContextParamSets.HIT_BLOCK);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    private static <T> void applyEffects(List<ConditionalEffect<T>> entries, LootContext lootContext, Consumer<T> effectConsumer) {
        for (ConditionalEffect<T> conditionalEffect : entries) {
            if (conditionalEffect.matches(lootContext)) {
                effectConsumer.accept(conditionalEffect.effect());
            }
        }
    }

    public void runLocationChangedEffects(ServerLevel world, int level, EnchantedItemInUse context, LivingEntity user) {
        if (context.inSlot() != null && !this.matchingSlot(context.inSlot())) {
            Set<EnchantmentLocationBasedEffect> set = user.activeLocationDependentEnchantments().remove(this);
            if (set != null) {
                set.forEach(effect -> effect.onDeactivated(context, user, user.position(), level));
            }
        } else {
            Set<EnchantmentLocationBasedEffect> set2 = user.activeLocationDependentEnchantments().get(this);

            for (ConditionalEffect<EnchantmentLocationBasedEffect> conditionalEffect : this.getEffects(EnchantmentEffectComponents.LOCATION_CHANGED)) {
                EnchantmentLocationBasedEffect enchantmentLocationBasedEffect = conditionalEffect.effect();
                boolean bl = set2 != null && set2.contains(enchantmentLocationBasedEffect);
                if (conditionalEffect.matches(locationContext(world, level, user, bl))) {
                    if (!bl) {
                        if (set2 == null) {
                            set2 = new ObjectArraySet<>();
                            user.activeLocationDependentEnchantments().put(this, set2);
                        }

                        set2.add(enchantmentLocationBasedEffect);
                    }

                    enchantmentLocationBasedEffect.onChangedBlock(world, level, context, user, user.position(), !bl);
                } else if (set2 != null && set2.remove(enchantmentLocationBasedEffect)) {
                    enchantmentLocationBasedEffect.onDeactivated(context, user, user.position(), level);
                }
            }

            if (set2 != null && set2.isEmpty()) {
                user.activeLocationDependentEnchantments().remove(this);
            }
        }
    }

    public void stopLocationBasedEffects(int level, EnchantedItemInUse context, LivingEntity user) {
        Set<EnchantmentLocationBasedEffect> set = user.activeLocationDependentEnchantments().remove(this);
        if (set != null) {
            for (EnchantmentLocationBasedEffect enchantmentLocationBasedEffect : set) {
                enchantmentLocationBasedEffect.onDeactivated(context, user, user.position(), level);
            }
        }
    }

    public static Enchantment.Builder enchantment(Enchantment.EnchantmentDefinition definition) {
        return new Enchantment.Builder(definition);
    }

    public static class Builder {
        private final Enchantment.EnchantmentDefinition definition;
        private HolderSet<Enchantment> exclusiveSet = HolderSet.direct();
        private final Map<DataComponentType<?>, List<?>> effectLists = new HashMap<>();
        private final DataComponentMap.Builder effectMapBuilder = DataComponentMap.builder();

        public Builder(Enchantment.EnchantmentDefinition properties) {
            this.definition = properties;
        }

        public Enchantment.Builder exclusiveWith(HolderSet<Enchantment> exclusiveSet) {
            this.exclusiveSet = exclusiveSet;
            return this;
        }

        public <E> Enchantment.Builder withEffect(DataComponentType<List<ConditionalEffect<E>>> effectType, E effect, LootItemCondition.Builder requirements) {
            this.getEffectsList(effectType).add(new ConditionalEffect<>(effect, Optional.of(requirements.build())));
            return this;
        }

        public <E> Enchantment.Builder withEffect(DataComponentType<List<ConditionalEffect<E>>> effectType, E effect) {
            this.getEffectsList(effectType).add(new ConditionalEffect<>(effect, Optional.empty()));
            return this;
        }

        public <E> Enchantment.Builder withEffect(
            DataComponentType<List<TargetedConditionalEffect<E>>> type,
            EnchantmentTarget enchanted,
            EnchantmentTarget affected,
            E effect,
            LootItemCondition.Builder requirements
        ) {
            this.getEffectsList(type).add(new TargetedConditionalEffect<>(enchanted, affected, effect, Optional.of(requirements.build())));
            return this;
        }

        public <E> Enchantment.Builder withEffect(
            DataComponentType<List<TargetedConditionalEffect<E>>> type, EnchantmentTarget enchanted, EnchantmentTarget affected, E effect
        ) {
            this.getEffectsList(type).add(new TargetedConditionalEffect<>(enchanted, affected, effect, Optional.empty()));
            return this;
        }

        public Enchantment.Builder withEffect(DataComponentType<List<EnchantmentAttributeEffect>> type, EnchantmentAttributeEffect effect) {
            this.getEffectsList(type).add(effect);
            return this;
        }

        public <E> Enchantment.Builder withSpecialEffect(DataComponentType<E> type, E effect) {
            this.effectMapBuilder.set(type, effect);
            return this;
        }

        public Enchantment.Builder withEffect(DataComponentType<Unit> type) {
            this.effectMapBuilder.set(type, Unit.INSTANCE);
            return this;
        }

        private <E> List<E> getEffectsList(DataComponentType<List<E>> type) {
            return (List<E>)this.effectLists.computeIfAbsent(type, typex -> {
                ArrayList<E> arrayList = new ArrayList<>();
                this.effectMapBuilder.set(type, arrayList);
                return arrayList;
            });
        }

        public Enchantment build(ResourceLocation id) {
            return new Enchantment(
                Component.translatable(Util.makeDescriptionId("enchantment", id)), this.definition, this.exclusiveSet, this.effectMapBuilder.build()
            );
        }
    }

    public static record Cost(int base, int perLevelAboveFirst) {
        public static final Codec<Enchantment.Cost> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        Codec.INT.fieldOf("base").forGetter(Enchantment.Cost::base),
                        Codec.INT.fieldOf("per_level_above_first").forGetter(Enchantment.Cost::perLevelAboveFirst)
                    )
                    .apply(instance, Enchantment.Cost::new)
        );

        public int calculate(int level) {
            return this.base + this.perLevelAboveFirst * (level - 1);
        }
    }

    public static record EnchantmentDefinition(
        HolderSet<Item> supportedItems,
        Optional<HolderSet<Item>> primaryItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        List<EquipmentSlotGroup> slots
    ) {
        public static final MapCodec<Enchantment.EnchantmentDefinition> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                        RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("supported_items").forGetter(Enchantment.EnchantmentDefinition::supportedItems),
                        RegistryCodecs.homogeneousList(Registries.ITEM)
                            .optionalFieldOf("primary_items")
                            .forGetter(Enchantment.EnchantmentDefinition::primaryItems),
                        ExtraCodecs.intRange(1, 1024).fieldOf("weight").forGetter(Enchantment.EnchantmentDefinition::weight),
                        ExtraCodecs.intRange(1, 255).fieldOf("max_level").forGetter(Enchantment.EnchantmentDefinition::maxLevel),
                        Enchantment.Cost.CODEC.fieldOf("min_cost").forGetter(Enchantment.EnchantmentDefinition::minCost),
                        Enchantment.Cost.CODEC.fieldOf("max_cost").forGetter(Enchantment.EnchantmentDefinition::maxCost),
                        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("anvil_cost").forGetter(Enchantment.EnchantmentDefinition::anvilCost),
                        EquipmentSlotGroup.CODEC.listOf().fieldOf("slots").forGetter(Enchantment.EnchantmentDefinition::slots)
                    )
                    .apply(instance, Enchantment.EnchantmentDefinition::new)
        );
    }
}
