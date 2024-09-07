package net.minecraft.world.item.enchantment;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableObject;

public class EnchantmentHelper {
    public static int getItemEnchantmentLevel(Holder<Enchantment> enchantment, ItemStack stack) {
        ItemEnchantments itemEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return itemEnchantments.getLevel(enchantment);
    }

    public static ItemEnchantments updateEnchantments(ItemStack stack, Consumer<ItemEnchantments.Mutable> applier) {
        DataComponentType<ItemEnchantments> dataComponentType = getComponentType(stack);
        ItemEnchantments itemEnchantments = stack.get(dataComponentType);
        if (itemEnchantments == null) {
            return ItemEnchantments.EMPTY;
        } else {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(itemEnchantments);
            applier.accept(mutable);
            ItemEnchantments itemEnchantments2 = mutable.toImmutable();
            stack.set(dataComponentType, itemEnchantments2);
            return itemEnchantments2;
        }
    }

    public static boolean canStoreEnchantments(ItemStack stack) {
        return stack.has(getComponentType(stack));
    }

    public static void setEnchantments(ItemStack stack, ItemEnchantments enchantments) {
        stack.set(getComponentType(stack), enchantments);
    }

    public static ItemEnchantments getEnchantmentsForCrafting(ItemStack stack) {
        return stack.getOrDefault(getComponentType(stack), ItemEnchantments.EMPTY);
    }

    private static DataComponentType<ItemEnchantments> getComponentType(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }

    public static boolean hasAnyEnchantments(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
            || !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    public static int processDurabilityChange(ServerLevel world, ItemStack stack, int baseItemDamage) {
        MutableFloat mutableFloat = new MutableFloat((float)baseItemDamage);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyDurabilityChange(world, level, stack, mutableFloat));
        return mutableFloat.intValue();
    }

    public static int processAmmoUse(ServerLevel world, ItemStack rangedWeaponStack, ItemStack projectileStack, int baseAmmoUse) {
        MutableFloat mutableFloat = new MutableFloat((float)baseAmmoUse);
        runIterationOnItem(rangedWeaponStack, (enchantment, level) -> enchantment.value().modifyAmmoCount(world, level, projectileStack, mutableFloat));
        return mutableFloat.intValue();
    }

    public static int processBlockExperience(ServerLevel world, ItemStack stack, int baseBlockExperience) {
        MutableFloat mutableFloat = new MutableFloat((float)baseBlockExperience);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyBlockExperience(world, level, stack, mutableFloat));
        return mutableFloat.intValue();
    }

    public static int processMobExperience(ServerLevel world, @Nullable Entity attacker, Entity mob, int baseMobExperience) {
        if (attacker instanceof LivingEntity livingEntity) {
            MutableFloat mutableFloat = new MutableFloat((float)baseMobExperience);
            runIterationOnEquipment(
                livingEntity, (enchantment, level, context) -> enchantment.value().modifyMobExperience(world, level, context.itemStack(), mob, mutableFloat)
            );
            return mutableFloat.intValue();
        } else {
            return baseMobExperience;
        }
    }

    private static void runIterationOnItem(ItemStack stack, EnchantmentHelper.EnchantmentVisitor consumer) {
        ItemEnchantments itemEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
            consumer.accept(entry.getKey(), entry.getIntValue());
        }
    }

    private static void runIterationOnItem(
        ItemStack stack, EquipmentSlot slot, LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor contextAwareConsumer
    ) {
        if (!stack.isEmpty()) {
            ItemEnchantments itemEnchantments = stack.get(DataComponents.ENCHANTMENTS);
            if (itemEnchantments != null && !itemEnchantments.isEmpty()) {
                EnchantedItemInUse enchantedItemInUse = new EnchantedItemInUse(stack, slot, entity);

                for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    if (holder.value().matchingSlot(slot)) {
                        contextAwareConsumer.accept(holder, entry.getIntValue(), enchantedItemInUse);
                    }
                }
            }
        }
    }

    private static void runIterationOnEquipment(LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor contextAwareConsumer) {
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            runIterationOnItem(entity.getItemBySlot(equipmentSlot), equipmentSlot, entity, contextAwareConsumer);
        }
    }

    public static boolean isImmuneToDamage(ServerLevel world, LivingEntity user, DamageSource damageSource) {
        MutableBoolean mutableBoolean = new MutableBoolean();
        runIterationOnEquipment(
            user,
            (enchantment, level, context) -> mutableBoolean.setValue(
                    mutableBoolean.isTrue() || enchantment.value().isImmuneToDamage(world, level, user, damageSource)
                )
        );
        return mutableBoolean.isTrue();
    }

    public static float getDamageProtection(ServerLevel world, LivingEntity user, DamageSource damageSource) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnEquipment(
            user,
            (enchantment, level, context) -> enchantment.value().modifyDamageProtection(world, level, context.itemStack(), user, damageSource, mutableFloat)
        );
        return mutableFloat.floatValue();
    }

    public static float modifyDamage(ServerLevel world, ItemStack stack, Entity target, DamageSource damageSource, float baseDamage) {
        MutableFloat mutableFloat = new MutableFloat(baseDamage);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyDamage(world, level, stack, target, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static float modifyFallBasedDamage(ServerLevel world, ItemStack stack, Entity target, DamageSource damageSource, float baseSmashDamagePerFallenBlock) {
        MutableFloat mutableFloat = new MutableFloat(baseSmashDamagePerFallenBlock);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyFallBasedDamage(world, level, stack, target, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static float modifyArmorEffectiveness(ServerLevel world, ItemStack stack, Entity user, DamageSource damageSource, float baseArmorEffectiveness) {
        MutableFloat mutableFloat = new MutableFloat(baseArmorEffectiveness);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyArmorEffectivness(world, level, stack, user, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static float modifyKnockback(ServerLevel world, ItemStack stack, Entity target, DamageSource damageSource, float baseKnockback) {
        MutableFloat mutableFloat = new MutableFloat(baseKnockback);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyKnockback(world, level, stack, target, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static void doPostAttackEffects(ServerLevel world, Entity target, DamageSource damageSource) {
        if (damageSource.getEntity() instanceof LivingEntity livingEntity) {
            doPostAttackEffectsWithItemSource(world, target, damageSource, livingEntity.getWeaponItem());
        } else {
            doPostAttackEffectsWithItemSource(world, target, damageSource, null);
        }
    }

    public static void doPostAttackEffectsWithItemSource(ServerLevel world, Entity target, DamageSource damageSource, @Nullable ItemStack weapon) {
        if (target instanceof LivingEntity livingEntity) {
            runIterationOnEquipment(
                livingEntity,
                (enchantment, level, context) -> enchantment.value().doPostAttack(world, level, context, EnchantmentTarget.VICTIM, target, damageSource)
            );
        }

        if (weapon != null && damageSource.getEntity() instanceof LivingEntity livingEntity2) {
            runIterationOnItem(
                weapon,
                EquipmentSlot.MAINHAND,
                livingEntity2,
                (enchantment, level, context) -> enchantment.value().doPostAttack(world, level, context, EnchantmentTarget.ATTACKER, target, damageSource)
            );
        }
    }

    public static void runLocationChangedEffects(ServerLevel world, LivingEntity user) {
        runIterationOnEquipment(user, (enchantment, level, context) -> enchantment.value().runLocationChangedEffects(world, level, context, user));
    }

    public static void runLocationChangedEffects(ServerLevel world, ItemStack stack, LivingEntity user, EquipmentSlot slot) {
        runIterationOnItem(stack, slot, user, (enchantment, level, context) -> enchantment.value().runLocationChangedEffects(world, level, context, user));
    }

    public static void stopLocationBasedEffects(LivingEntity user) {
        runIterationOnEquipment(user, (enchantment, level, context) -> enchantment.value().stopLocationBasedEffects(level, context, user));
    }

    public static void stopLocationBasedEffects(ItemStack stack, LivingEntity user, EquipmentSlot slot) {
        runIterationOnItem(stack, slot, user, (enchantment, level, context) -> enchantment.value().stopLocationBasedEffects(level, context, user));
    }

    public static void tickEffects(ServerLevel world, LivingEntity user) {
        runIterationOnEquipment(user, (enchantment, level, context) -> enchantment.value().tick(world, level, context, user));
    }

    public static int getEnchantmentLevel(Holder<Enchantment> enchantment, LivingEntity entity) {
        Iterable<ItemStack> iterable = enchantment.value().getSlotItems(entity).values();
        int i = 0;

        for (ItemStack itemStack : iterable) {
            int j = getItemEnchantmentLevel(enchantment, itemStack);
            if (j > i) {
                i = j;
            }
        }

        return i;
    }

    public static int processProjectileCount(ServerLevel world, ItemStack stack, Entity user, int baseProjectileCount) {
        MutableFloat mutableFloat = new MutableFloat((float)baseProjectileCount);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyProjectileCount(world, level, stack, user, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static float processProjectileSpread(ServerLevel world, ItemStack stack, Entity user, float baseProjectileSpread) {
        MutableFloat mutableFloat = new MutableFloat(baseProjectileSpread);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyProjectileSpread(world, level, stack, user, mutableFloat));
        return Math.max(0.0F, mutableFloat.floatValue());
    }

    public static int getPiercingCount(ServerLevel world, ItemStack weaponStack, ItemStack projectileStack) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(weaponStack, (enchantment, level) -> enchantment.value().modifyPiercingCount(world, level, projectileStack, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static void onProjectileSpawned(ServerLevel world, ItemStack weaponStack, AbstractArrow projectileEntity, Consumer<Item> onBreak) {
        LivingEntity livingEntity2 = projectileEntity.getOwner() instanceof LivingEntity livingEntity ? livingEntity : null;
        EnchantedItemInUse enchantedItemInUse = new EnchantedItemInUse(weaponStack, null, livingEntity2, onBreak);
        runIterationOnItem(weaponStack, (enchantment, level) -> enchantment.value().onProjectileSpawned(world, level, enchantedItemInUse, projectileEntity));
    }

    public static void onHitBlock(
        ServerLevel world,
        ItemStack stack,
        @Nullable LivingEntity user,
        Entity enchantedEntity,
        @Nullable EquipmentSlot slot,
        Vec3 pos,
        BlockState state,
        Consumer<Item> onBreak
    ) {
        EnchantedItemInUse enchantedItemInUse = new EnchantedItemInUse(stack, slot, user, onBreak);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().onHitBlock(world, level, enchantedItemInUse, enchantedEntity, pos, state));
    }

    public static int modifyDurabilityToRepairFromXp(ServerLevel world, ItemStack stack, int baseRepairWithXp) {
        MutableFloat mutableFloat = new MutableFloat((float)baseRepairWithXp);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyDurabilityToRepairFromXp(world, level, stack, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static float processEquipmentDropChance(ServerLevel world, LivingEntity attacker, DamageSource damageSource, float baseEquipmentDropChance) {
        MutableFloat mutableFloat = new MutableFloat(baseEquipmentDropChance);
        RandomSource randomSource = attacker.getRandom();
        runIterationOnEquipment(attacker, (enchantment, level, context) -> {
            LootContext lootContext = Enchantment.damageContext(world, level, attacker, damageSource);
            enchantment.value().getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS).forEach(effect -> {
                if (effect.enchanted() == EnchantmentTarget.VICTIM && effect.affected() == EnchantmentTarget.VICTIM && effect.matches(lootContext)) {
                    mutableFloat.setValue(effect.effect().process(level, randomSource, mutableFloat.floatValue()));
                }
            });
        });
        if (damageSource.getEntity() instanceof LivingEntity livingEntity) {
            runIterationOnEquipment(livingEntity, (enchantment, level, context) -> {
                LootContext lootContext = Enchantment.damageContext(world, level, attacker, damageSource);
                enchantment.value().getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS).forEach(effect -> {
                    if (effect.enchanted() == EnchantmentTarget.ATTACKER && effect.affected() == EnchantmentTarget.VICTIM && effect.matches(lootContext)) {
                        mutableFloat.setValue(effect.effect().process(level, randomSource, mutableFloat.floatValue()));
                    }
                });
            });
        }

        return mutableFloat.floatValue();
    }

    public static void forEachModifier(ItemStack stack, EquipmentSlotGroup slot, BiConsumer<Holder<Attribute>, AttributeModifier> attributeModifierConsumer) {
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach(effect -> {
                if (((Enchantment)enchantment.value()).definition().slots().contains(slot)) {
                    attributeModifierConsumer.accept(effect.attribute(), effect.getModifier(level, slot));
                }
            }));
    }

    public static void forEachModifier(ItemStack stack, EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> attributeModifierConsumer) {
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach(effect -> {
                if (((Enchantment)enchantment.value()).matchingSlot(slot)) {
                    attributeModifierConsumer.accept(effect.attribute(), effect.getModifier(level, slot));
                }
            }));
    }

    public static int getFishingLuckBonus(ServerLevel world, ItemStack stack, Entity user) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyFishingLuckBonus(world, level, stack, user, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static float getFishingTimeReduction(ServerLevel world, ItemStack stack, Entity user) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyFishingTimeReduction(world, level, stack, user, mutableFloat));
        return Math.max(0.0F, mutableFloat.floatValue());
    }

    public static int getTridentReturnToOwnerAcceleration(ServerLevel world, ItemStack stack, Entity user) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyTridentReturnToOwnerAcceleration(world, level, stack, user, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static float modifyCrossbowChargingTime(ItemStack stack, LivingEntity user, float baseCrossbowChargeTime) {
        MutableFloat mutableFloat = new MutableFloat(baseCrossbowChargeTime);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyCrossbowChargeTime(user.getRandom(), level, mutableFloat));
        return Math.max(0.0F, mutableFloat.floatValue());
    }

    public static float getTridentSpinAttackStrength(ItemStack stack, LivingEntity user) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(stack, (enchantment, level) -> enchantment.value().modifyTridentSpinAttackStrength(user.getRandom(), level, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static boolean hasTag(ItemStack stack, TagKey<Enchantment> tag) {
        ItemEnchantments itemEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.is(tag)) {
                return true;
            }
        }

        return false;
    }

    public static boolean has(ItemStack stack, DataComponentType<?> componentType) {
        MutableBoolean mutableBoolean = new MutableBoolean(false);
        runIterationOnItem(stack, (enchantment, level) -> {
            if (enchantment.value().effects().has(componentType)) {
                mutableBoolean.setTrue();
            }
        });
        return mutableBoolean.booleanValue();
    }

    public static <T> Optional<T> pickHighestLevel(ItemStack stack, DataComponentType<List<T>> componentType) {
        Pair<List<T>, Integer> pair = getHighestLevel(stack, componentType);
        if (pair != null) {
            List<T> list = pair.getFirst();
            int i = pair.getSecond();
            return Optional.of(list.get(Math.min(i, list.size()) - 1));
        } else {
            return Optional.empty();
        }
    }

    @Nullable
    public static <T> Pair<T, Integer> getHighestLevel(ItemStack stack, DataComponentType<T> componentType) {
        MutableObject<Pair<T, Integer>> mutableObject = new MutableObject<>();
        runIterationOnItem(stack, (enchantment, level) -> {
            if (mutableObject.getValue() == null || mutableObject.getValue().getSecond() < level) {
                T object = enchantment.value().effects().get(componentType);
                if (object != null) {
                    mutableObject.setValue(Pair.of(object, level));
                }
            }
        });
        return mutableObject.getValue();
    }

    public static Optional<EnchantedItemInUse> getRandomItemWith(DataComponentType<?> componentType, LivingEntity entity, Predicate<ItemStack> stackPredicate) {
        List<EnchantedItemInUse> list = new ArrayList<>();

        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            ItemStack itemStack = entity.getItemBySlot(equipmentSlot);
            if (stackPredicate.test(itemStack)) {
                ItemEnchantments itemEnchantments = itemStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

                for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    if (holder.value().effects().has(componentType) && holder.value().matchingSlot(equipmentSlot)) {
                        list.add(new EnchantedItemInUse(itemStack, equipmentSlot, entity));
                    }
                }
            }
        }

        return Util.getRandomSafe(list, entity.getRandom());
    }

    public static int getEnchantmentCost(RandomSource random, int slotIndex, int bookshelfCount, ItemStack stack) {
        Item item = stack.getItem();
        int i = item.getEnchantmentValue();
        if (i <= 0) {
            return 0;
        } else {
            if (bookshelfCount > 15) {
                bookshelfCount = 15;
            }

            int j = random.nextInt(8) + 1 + (bookshelfCount >> 1) + random.nextInt(bookshelfCount + 1);
            if (slotIndex == 0) {
                return Math.max(j / 3, 1);
            } else {
                return slotIndex == 1 ? j * 2 / 3 + 1 : Math.max(j, bookshelfCount * 2);
            }
        }
    }

    public static ItemStack enchantItem(
        RandomSource random, ItemStack stack, int level, RegistryAccess dynamicRegistryManager, Optional<? extends HolderSet<Enchantment>> enchantments
    ) {
        return enchantItem(
            random,
            stack,
            level,
            enchantments.map(HolderSet::stream)
                .orElseGet(() -> dynamicRegistryManager.registryOrThrow(Registries.ENCHANTMENT).holders().map(reference -> (Holder<Enchantment>)reference))
        );
    }

    public static ItemStack enchantItem(RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = selectEnchantment(random, stack, level, possibleEnchantments);
        if (stack.is(Items.BOOK)) {
            stack = new ItemStack(Items.ENCHANTED_BOOK);
        }

        for (EnchantmentInstance enchantmentInstance : list) {
            stack.enchant(enchantmentInstance.enchantment, enchantmentInstance.level);
        }

        return stack;
    }

    public static List<EnchantmentInstance> selectEnchantment(RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        Item item = stack.getItem();
        int i = item.getEnchantmentValue();
        if (i <= 0) {
            return list;
        } else {
            level += 1 + random.nextInt(i / 4 + 1) + random.nextInt(i / 4 + 1);
            float f = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
            level = Mth.clamp(Math.round((float)level + (float)level * f), 1, Integer.MAX_VALUE);
            List<EnchantmentInstance> list2 = getAvailableEnchantmentResults(level, stack, possibleEnchantments);
            if (!list2.isEmpty()) {
                WeightedRandom.getRandomItem(random, list2).ifPresent(list::add);

                while (random.nextInt(50) <= level) {
                    if (!list.isEmpty()) {
                        filterCompatibleEnchantments(list2, Util.lastOf(list));
                    }

                    if (list2.isEmpty()) {
                        break;
                    }

                    WeightedRandom.getRandomItem(random, list2).ifPresent(list::add);
                    level /= 2;
                }
            }

            return list;
        }
    }

    public static void filterCompatibleEnchantments(List<EnchantmentInstance> possibleEntries, EnchantmentInstance pickedEntry) {
        possibleEntries.removeIf(entry -> !Enchantment.areCompatible(pickedEntry.enchantment, entry.enchantment));
    }

    public static boolean isEnchantmentCompatible(Collection<Holder<Enchantment>> existing, Holder<Enchantment> candidate) {
        for (Holder<Enchantment> holder : existing) {
            if (!Enchantment.areCompatible(holder, candidate)) {
                return false;
            }
        }

        return true;
    }

    public static List<EnchantmentInstance> getAvailableEnchantmentResults(int level, ItemStack stack, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        boolean bl = stack.is(Items.BOOK);
        possibleEnchantments.filter(enchantment -> enchantment.value().isPrimaryItem(stack) || bl).forEach(enchantmentx -> {
            Enchantment enchantment = enchantmentx.value();

            for (int j = enchantment.getMaxLevel(); j >= enchantment.getMinLevel(); j--) {
                if (level >= enchantment.getMinCost(j) && level <= enchantment.getMaxCost(j)) {
                    list.add(new EnchantmentInstance((Holder<Enchantment>)enchantmentx, j));
                    break;
                }
            }
        });
        return list;
    }

    public static void enchantItemFromProvider(
        ItemStack stack, RegistryAccess registryManager, ResourceKey<EnchantmentProvider> providerKey, DifficultyInstance localDifficulty, RandomSource random
    ) {
        EnchantmentProvider enchantmentProvider = registryManager.registryOrThrow(Registries.ENCHANTMENT_PROVIDER).get(providerKey);
        if (enchantmentProvider != null) {
            updateEnchantments(stack, componentBuilder -> enchantmentProvider.enchant(stack, componentBuilder, random, localDifficulty));
        }
    }

    @FunctionalInterface
    interface EnchantmentInSlotVisitor {
        void accept(Holder<Enchantment> enchantment, int level, EnchantedItemInUse context);
    }

    @FunctionalInterface
    interface EnchantmentVisitor {
        void accept(Holder<Enchantment> enchantment, int level);
    }
}
