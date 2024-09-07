package net.minecraft.data.loot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.advancements.critereon.EnchantmentPredicate;
import net.minecraft.advancements.critereon.ItemEnchantmentsPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.ItemSubPredicates;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.ApplyExplosionDecay;
import net.minecraft.world.level.storage.loot.functions.CopyBlockState;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LimitCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraft.world.level.storage.loot.providers.number.BinomialDistributionGenerator;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

public abstract class BlockLootSubProvider implements LootTableSubProvider {
    protected static final LootItemCondition.Builder HAS_SHEARS = MatchTool.toolMatches(ItemPredicate.Builder.item().of(Items.SHEARS));
    protected final HolderLookup.Provider registries;
    protected final Set<Item> explosionResistant;
    protected final FeatureFlagSet enabledFeatures;
    protected final Map<ResourceKey<LootTable>, LootTable.Builder> map;
    protected static final float[] NORMAL_LEAVES_SAPLING_CHANCES = new float[]{0.05F, 0.0625F, 0.083333336F, 0.1F};
    private static final float[] NORMAL_LEAVES_STICK_CHANCES = new float[]{0.02F, 0.022222223F, 0.025F, 0.033333335F, 0.1F};

    protected LootItemCondition.Builder hasSilkTouch() {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return MatchTool.toolMatches(
            ItemPredicate.Builder.item()
                .withSubPredicate(
                    ItemSubPredicates.ENCHANTMENTS,
                    ItemEnchantmentsPredicate.enchantments(
                        List.of(new EnchantmentPredicate(registryLookup.getOrThrow(Enchantments.SILK_TOUCH), MinMaxBounds.Ints.atLeast(1)))
                    )
                )
        );
    }

    protected LootItemCondition.Builder doesNotHaveSilkTouch() {
        return this.hasSilkTouch().invert();
    }

    private LootItemCondition.Builder hasShearsOrSilkTouch() {
        return HAS_SHEARS.or(this.hasSilkTouch());
    }

    private LootItemCondition.Builder doesNotHaveShearsOrSilkTouch() {
        return this.hasShearsOrSilkTouch().invert();
    }

    protected BlockLootSubProvider(Set<Item> explosionImmuneItems, FeatureFlagSet requiredFeatures, HolderLookup.Provider registryLookup) {
        this(explosionImmuneItems, requiredFeatures, new HashMap<>(), registryLookup);
    }

    protected BlockLootSubProvider(
        Set<Item> explosionImmuneItems,
        FeatureFlagSet requiredFeatures,
        Map<ResourceKey<LootTable>, LootTable.Builder> lootTables,
        HolderLookup.Provider registryLookup
    ) {
        this.explosionResistant = explosionImmuneItems;
        this.enabledFeatures = requiredFeatures;
        this.map = lootTables;
        this.registries = registryLookup;
    }

    protected <T extends FunctionUserBuilder<T>> T applyExplosionDecay(ItemLike drop, FunctionUserBuilder<T> builder) {
        return !this.explosionResistant.contains(drop.asItem()) ? builder.apply(ApplyExplosionDecay.explosionDecay()) : builder.unwrap();
    }

    protected <T extends ConditionUserBuilder<T>> T applyExplosionCondition(ItemLike drop, ConditionUserBuilder<T> builder) {
        return !this.explosionResistant.contains(drop.asItem()) ? builder.when(ExplosionCondition.survivesExplosion()) : builder.unwrap();
    }

    public LootTable.Builder createSingleItemTable(ItemLike drop) {
        return LootTable.lootTable()
            .withPool(this.applyExplosionCondition(drop, LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(drop))));
    }

    private static LootTable.Builder createSelfDropDispatchTable(
        Block drop, LootItemCondition.Builder conditionBuilder, LootPoolEntryContainer.Builder<?> child
    ) {
        return LootTable.lootTable()
            .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(drop).when(conditionBuilder).otherwise(child)));
    }

    protected LootTable.Builder createSilkTouchDispatchTable(Block block, LootPoolEntryContainer.Builder<?> loot) {
        return createSelfDropDispatchTable(block, this.hasSilkTouch(), loot);
    }

    protected LootTable.Builder createShearsDispatchTable(Block block, LootPoolEntryContainer.Builder<?> loot) {
        return createSelfDropDispatchTable(block, HAS_SHEARS, loot);
    }

    protected LootTable.Builder createSilkTouchOrShearsDispatchTable(Block block, LootPoolEntryContainer.Builder<?> loot) {
        return createSelfDropDispatchTable(block, this.hasShearsOrSilkTouch(), loot);
    }

    protected LootTable.Builder createSingleItemTableWithSilkTouch(Block withSilkTouch, ItemLike withoutSilkTouch) {
        return this.createSilkTouchDispatchTable(
            withSilkTouch, (LootPoolEntryContainer.Builder<?>)this.applyExplosionCondition(withSilkTouch, LootItem.lootTableItem(withoutSilkTouch))
        );
    }

    protected LootTable.Builder createSingleItemTable(ItemLike drop, NumberProvider count) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(
                        (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
                            drop, LootItem.lootTableItem(drop).apply(SetItemCountFunction.setCount(count))
                        )
                    )
            );
    }

    protected LootTable.Builder createSingleItemTableWithSilkTouch(Block block, ItemLike drop, NumberProvider count) {
        return this.createSilkTouchDispatchTable(
            block, (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(block, LootItem.lootTableItem(drop).apply(SetItemCountFunction.setCount(count)))
        );
    }

    private LootTable.Builder createSilkTouchOnlyTable(ItemLike drop) {
        return LootTable.lootTable()
            .withPool(LootPool.lootPool().when(this.hasSilkTouch()).setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(drop)));
    }

    private LootTable.Builder createPotFlowerItemTable(ItemLike drop) {
        return LootTable.lootTable()
            .withPool(
                this.applyExplosionCondition(
                    Blocks.FLOWER_POT, LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(Blocks.FLOWER_POT))
                )
            )
            .withPool(this.applyExplosionCondition(drop, LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(drop))));
    }

    protected LootTable.Builder createSlabItemTable(Block drop) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(
                        (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
                            drop,
                            LootItem.lootTableItem(drop)
                                .apply(
                                    SetItemCountFunction.setCount(ConstantValue.exactly(2.0F))
                                        .when(
                                            LootItemBlockStatePropertyCondition.hasBlockStateProperties(drop)
                                                .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(SlabBlock.TYPE, SlabType.DOUBLE))
                                        )
                                )
                        )
                    )
            );
    }

    protected <T extends Comparable<T> & StringRepresentable> LootTable.Builder createSinglePropConditionTable(Block drop, Property<T> property, T value) {
        return LootTable.lootTable()
            .withPool(
                this.applyExplosionCondition(
                    drop,
                    LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(
                            LootItem.lootTableItem(drop)
                                .when(
                                    LootItemBlockStatePropertyCondition.hasBlockStateProperties(drop)
                                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(property, value))
                                )
                        )
                )
            );
    }

    protected LootTable.Builder createNameableBlockEntityTable(Block drop) {
        return LootTable.lootTable()
            .withPool(
                this.applyExplosionCondition(
                    drop,
                    LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(
                            LootItem.lootTableItem(drop)
                                .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY).include(DataComponents.CUSTOM_NAME))
                        )
                )
            );
    }

    protected LootTable.Builder createShulkerBoxDrop(Block drop) {
        return LootTable.lootTable()
            .withPool(
                this.applyExplosionCondition(
                    drop,
                    LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(
                            LootItem.lootTableItem(drop)
                                .apply(
                                    CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                                        .include(DataComponents.CUSTOM_NAME)
                                        .include(DataComponents.CONTAINER)
                                        .include(DataComponents.LOCK)
                                        .include(DataComponents.CONTAINER_LOOT)
                                )
                        )
                )
            );
    }

    protected LootTable.Builder createCopperOreDrops(Block drop) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(
            drop,
            (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
                drop,
                LootItem.lootTableItem(Items.RAW_COPPER)
                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(2.0F, 5.0F)))
                    .apply(ApplyBonusCount.addOreBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE)))
            )
        );
    }

    protected LootTable.Builder createLapisOreDrops(Block drop) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(
            drop,
            (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
                drop,
                LootItem.lootTableItem(Items.LAPIS_LAZULI)
                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(4.0F, 9.0F)))
                    .apply(ApplyBonusCount.addOreBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE)))
            )
        );
    }

    protected LootTable.Builder createRedstoneOreDrops(Block drop) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(
            drop,
            (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
                drop,
                LootItem.lootTableItem(Items.REDSTONE)
                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(4.0F, 5.0F)))
                    .apply(ApplyBonusCount.addUniformBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE)))
            )
        );
    }

    protected LootTable.Builder createBannerDrop(Block drop) {
        return LootTable.lootTable()
            .withPool(
                this.applyExplosionCondition(
                    drop,
                    LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(
                            LootItem.lootTableItem(drop)
                                .apply(
                                    CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                                        .include(DataComponents.CUSTOM_NAME)
                                        .include(DataComponents.ITEM_NAME)
                                        .include(DataComponents.HIDE_ADDITIONAL_TOOLTIP)
                                        .include(DataComponents.BANNER_PATTERNS)
                                )
                        )
                )
            );
    }

    protected LootTable.Builder createBeeNestDrop(Block drop) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .when(this.hasSilkTouch())
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(
                        LootItem.lootTableItem(drop)
                            .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY).include(DataComponents.BEES))
                            .apply(CopyBlockState.copyState(drop).copy(BeehiveBlock.HONEY_LEVEL))
                    )
            );
    }

    protected LootTable.Builder createBeeHiveDrop(Block drop) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(
                        LootItem.lootTableItem(drop)
                            .when(this.hasSilkTouch())
                            .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY).include(DataComponents.BEES))
                            .apply(CopyBlockState.copyState(drop).copy(BeehiveBlock.HONEY_LEVEL))
                            .otherwise(LootItem.lootTableItem(drop))
                    )
            );
    }

    protected LootTable.Builder createCaveVinesDrop(Block drop) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .add(LootItem.lootTableItem(Items.GLOW_BERRIES))
                    .when(
                        LootItemBlockStatePropertyCondition.hasBlockStateProperties(drop)
                            .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(CaveVines.BERRIES, true))
                    )
            );
    }

    protected LootTable.Builder createOreDrop(Block withSilkTouch, Item withoutSilkTouch) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(
            withSilkTouch,
            (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
                withSilkTouch,
                LootItem.lootTableItem(withoutSilkTouch).apply(ApplyBonusCount.addOreBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE)))
            )
        );
    }

    protected LootTable.Builder createMushroomBlockDrop(Block withSilkTouch, ItemLike withoutSilkTouch) {
        return this.createSilkTouchDispatchTable(
            withSilkTouch,
            (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
                withSilkTouch,
                LootItem.lootTableItem(withoutSilkTouch)
                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(-6.0F, 2.0F)))
                    .apply(LimitCount.limitCount(IntRange.lowerBound(0)))
            )
        );
    }

    protected LootTable.Builder createGrassDrops(Block withShears) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createShearsDispatchTable(
            withShears,
            (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
                withShears,
                LootItem.lootTableItem(Items.WHEAT_SEEDS)
                    .when(LootItemRandomChanceCondition.randomChance(0.125F))
                    .apply(ApplyBonusCount.addUniformBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE), 2))
            )
        );
    }

    public LootTable.Builder createStemDrops(Block stem, Item drop) {
        return LootTable.lootTable()
            .withPool(
                this.applyExplosionDecay(
                    stem,
                    LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(
                            LootItem.lootTableItem(drop)
                                .apply(
                                    StemBlock.AGE.getPossibleValues(),
                                    age -> SetItemCountFunction.setCount(BinomialDistributionGenerator.binomial(3, (float)(age + 1) / 15.0F))
                                            .when(
                                                LootItemBlockStatePropertyCondition.hasBlockStateProperties(stem)
                                                    .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(StemBlock.AGE, age.intValue()))
                                            )
                                )
                        )
                )
            );
    }

    public LootTable.Builder createAttachedStemDrops(Block stem, Item drop) {
        return LootTable.lootTable()
            .withPool(
                this.applyExplosionDecay(
                    stem,
                    LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(drop).apply(SetItemCountFunction.setCount(BinomialDistributionGenerator.binomial(3, 0.53333336F))))
                )
            );
    }

    protected static LootTable.Builder createShearsOnlyDrop(ItemLike drop) {
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).when(HAS_SHEARS).add(LootItem.lootTableItem(drop)));
    }

    protected LootTable.Builder createMultifaceBlockDrops(Block drop, LootItemCondition.Builder condition) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .add(
                        (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
                            drop,
                            LootItem.lootTableItem(drop)
                                .when(condition)
                                .apply(
                                    Direction.values(),
                                    direction -> SetItemCountFunction.setCount(ConstantValue.exactly(1.0F), true)
                                            .when(
                                                LootItemBlockStatePropertyCondition.hasBlockStateProperties(drop)
                                                    .setProperties(
                                                        StatePropertiesPredicate.Builder.properties()
                                                            .hasProperty(MultifaceBlock.getFaceProperty(direction), true)
                                                    )
                                            )
                                )
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(-1.0F), true))
                        )
                    )
            );
    }

    protected LootTable.Builder createLeavesDrops(Block leaves, Block sapling, float... saplingChance) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchOrShearsDispatchTable(
                leaves,
                ((LootPoolSingletonContainer.Builder)this.applyExplosionCondition(leaves, LootItem.lootTableItem(sapling)))
                    .when(BonusLevelTableCondition.bonusLevelFlatChance(registryLookup.getOrThrow(Enchantments.FORTUNE), saplingChance))
            )
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(this.doesNotHaveShearsOrSilkTouch())
                    .add(
                        ((LootPoolSingletonContainer.Builder)this.applyExplosionDecay(
                                leaves, LootItem.lootTableItem(Items.STICK).apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
                            ))
                            .when(BonusLevelTableCondition.bonusLevelFlatChance(registryLookup.getOrThrow(Enchantments.FORTUNE), NORMAL_LEAVES_STICK_CHANCES))
                    )
            );
    }

    protected LootTable.Builder createOakLeavesDrops(Block leaves, Block sapling, float... saplingChance) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createLeavesDrops(leaves, sapling, saplingChance)
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(this.doesNotHaveShearsOrSilkTouch())
                    .add(
                        ((LootPoolSingletonContainer.Builder)this.applyExplosionCondition(leaves, LootItem.lootTableItem(Items.APPLE)))
                            .when(
                                BonusLevelTableCondition.bonusLevelFlatChance(
                                    registryLookup.getOrThrow(Enchantments.FORTUNE), 0.005F, 0.0055555557F, 0.00625F, 0.008333334F, 0.025F
                                )
                            )
                    )
            );
    }

    protected LootTable.Builder createMangroveLeavesDrops(Block leaves) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchOrShearsDispatchTable(
            leaves,
            ((LootPoolSingletonContainer.Builder)this.applyExplosionDecay(
                    Blocks.MANGROVE_LEAVES, LootItem.lootTableItem(Items.STICK).apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
                ))
                .when(BonusLevelTableCondition.bonusLevelFlatChance(registryLookup.getOrThrow(Enchantments.FORTUNE), NORMAL_LEAVES_STICK_CHANCES))
        );
    }

    protected LootTable.Builder createCropDrops(Block crop, Item product, Item seeds, LootItemCondition.Builder condition) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.applyExplosionDecay(
            crop,
            LootTable.lootTable()
                .withPool(LootPool.lootPool().add(LootItem.lootTableItem(product).when(condition).otherwise(LootItem.lootTableItem(seeds))))
                .withPool(
                    LootPool.lootPool()
                        .when(condition)
                        .add(
                            LootItem.lootTableItem(seeds)
                                .apply(ApplyBonusCount.addBonusBinomialDistributionCount(registryLookup.getOrThrow(Enchantments.FORTUNE), 0.5714286F, 3))
                        )
                )
        );
    }

    protected LootTable.Builder createDoublePlantShearsDrop(Block seagrass) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool().when(HAS_SHEARS).add(LootItem.lootTableItem(seagrass).apply(SetItemCountFunction.setCount(ConstantValue.exactly(2.0F))))
            );
    }

    protected LootTable.Builder createDoublePlantWithSeedDrops(Block tallPlant, Block shortPlant) {
        LootPoolEntryContainer.Builder<?> builder = LootItem.lootTableItem(shortPlant)
            .apply(SetItemCountFunction.setCount(ConstantValue.exactly(2.0F)))
            .when(HAS_SHEARS)
            .otherwise(
                ((LootPoolSingletonContainer.Builder)this.applyExplosionCondition(tallPlant, LootItem.lootTableItem(Items.WHEAT_SEEDS)))
                    .when(LootItemRandomChanceCondition.randomChance(0.125F))
            );
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .add(builder)
                    .when(
                        LootItemBlockStatePropertyCondition.hasBlockStateProperties(tallPlant)
                            .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER))
                    )
                    .when(
                        LocationCheck.checkLocation(
                            LocationPredicate.Builder.location()
                                .setBlock(
                                    BlockPredicate.Builder.block()
                                        .of(tallPlant)
                                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER))
                                ),
                            new BlockPos(0, 1, 0)
                        )
                    )
            )
            .withPool(
                LootPool.lootPool()
                    .add(builder)
                    .when(
                        LootItemBlockStatePropertyCondition.hasBlockStateProperties(tallPlant)
                            .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER))
                    )
                    .when(
                        LocationCheck.checkLocation(
                            LocationPredicate.Builder.location()
                                .setBlock(
                                    BlockPredicate.Builder.block()
                                        .of(tallPlant)
                                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER))
                                ),
                            new BlockPos(0, -1, 0)
                        )
                    )
            );
    }

    protected LootTable.Builder createCandleDrops(Block candle) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(
                        (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
                            candle,
                            LootItem.lootTableItem(candle)
                                .apply(
                                    List.of(2, 3, 4),
                                    candles -> SetItemCountFunction.setCount(ConstantValue.exactly((float)candles.intValue()))
                                            .when(
                                                LootItemBlockStatePropertyCondition.hasBlockStateProperties(candle)
                                                    .setProperties(
                                                        StatePropertiesPredicate.Builder.properties().hasProperty(CandleBlock.CANDLES, candles.intValue())
                                                    )
                                            )
                                )
                        )
                    )
            );
    }

    protected LootTable.Builder createPetalsDrops(Block flowerbed) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(
                        (LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
                            flowerbed,
                            LootItem.lootTableItem(flowerbed)
                                .apply(
                                    IntStream.rangeClosed(1, 4).boxed().toList(),
                                    flowerAmount -> SetItemCountFunction.setCount(ConstantValue.exactly((float)flowerAmount.intValue()))
                                            .when(
                                                LootItemBlockStatePropertyCondition.hasBlockStateProperties(flowerbed)
                                                    .setProperties(
                                                        StatePropertiesPredicate.Builder.properties()
                                                            .hasProperty(PinkPetalsBlock.AMOUNT, flowerAmount.intValue())
                                                    )
                                            )
                                )
                        )
                    )
            );
    }

    protected static LootTable.Builder createCandleCakeDrops(Block candleCake) {
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(candleCake)));
    }

    public static LootTable.Builder noDrop() {
        return LootTable.lootTable();
    }

    protected abstract void generate();

    @Override
    public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> lootTableBiConsumer) {
        this.generate();
        Set<ResourceKey<LootTable>> set = new HashSet<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.isEnabled(this.enabledFeatures)) {
                ResourceKey<LootTable> resourceKey = block.getLootTable();
                if (resourceKey != BuiltInLootTables.EMPTY && set.add(resourceKey)) {
                    LootTable.Builder builder = this.map.remove(resourceKey);
                    if (builder == null) {
                        throw new IllegalStateException(
                            String.format(Locale.ROOT, "Missing loottable '%s' for '%s'", resourceKey.location(), BuiltInRegistries.BLOCK.getKey(block))
                        );
                    }

                    lootTableBiConsumer.accept(resourceKey, builder);
                }
            }
        }

        if (!this.map.isEmpty()) {
            throw new IllegalStateException("Created block loot tables for non-blocks: " + this.map.keySet());
        }
    }

    protected void addNetherVinesDropTable(Block vine, Block vinePlant) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        LootTable.Builder builder = this.createSilkTouchOrShearsDispatchTable(
            vine,
            LootItem.lootTableItem(vine)
                .when(BonusLevelTableCondition.bonusLevelFlatChance(registryLookup.getOrThrow(Enchantments.FORTUNE), 0.33F, 0.55F, 0.77F, 1.0F))
        );
        this.add(vine, builder);
        this.add(vinePlant, builder);
    }

    protected LootTable.Builder createDoorTable(Block block) {
        return this.createSinglePropConditionTable(block, DoorBlock.HALF, DoubleBlockHalf.LOWER);
    }

    protected void dropPottedContents(Block block) {
        this.add(block, flowerPot -> this.createPotFlowerItemTable(((FlowerPotBlock)flowerPot).getPotted()));
    }

    protected void otherWhenSilkTouch(Block block, Block drop) {
        this.add(block, this.createSilkTouchOnlyTable(drop));
    }

    protected void dropOther(Block block, ItemLike drop) {
        this.add(block, this.createSingleItemTable(drop));
    }

    protected void dropWhenSilkTouch(Block block) {
        this.otherWhenSilkTouch(block, block);
    }

    protected void dropSelf(Block block) {
        this.dropOther(block, block);
    }

    protected void add(Block block, Function<Block, LootTable.Builder> lootTableFunction) {
        this.add(block, lootTableFunction.apply(block));
    }

    protected void add(Block block, LootTable.Builder lootTable) {
        this.map.put(block.getLootTable(), lootTable);
    }
}
