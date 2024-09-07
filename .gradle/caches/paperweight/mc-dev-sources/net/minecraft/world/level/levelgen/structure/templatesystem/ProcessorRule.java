package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.Passthrough;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.RuleBlockEntityModifier;

public class ProcessorRule {
    public static final Passthrough DEFAULT_BLOCK_ENTITY_MODIFIER = Passthrough.INSTANCE;
    public static final Codec<ProcessorRule> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    RuleTest.CODEC.fieldOf("input_predicate").forGetter(rule -> rule.inputPredicate),
                    RuleTest.CODEC.fieldOf("location_predicate").forGetter(rule -> rule.locPredicate),
                    PosRuleTest.CODEC.lenientOptionalFieldOf("position_predicate", PosAlwaysTrueTest.INSTANCE).forGetter(rule -> rule.posPredicate),
                    BlockState.CODEC.fieldOf("output_state").forGetter(rule -> rule.outputState),
                    RuleBlockEntityModifier.CODEC
                        .lenientOptionalFieldOf("block_entity_modifier", DEFAULT_BLOCK_ENTITY_MODIFIER)
                        .forGetter(rule -> rule.blockEntityModifier)
                )
                .apply(instance, ProcessorRule::new)
    );
    private final RuleTest inputPredicate;
    private final RuleTest locPredicate;
    private final PosRuleTest posPredicate;
    private final BlockState outputState;
    private final RuleBlockEntityModifier blockEntityModifier;

    public ProcessorRule(RuleTest inputPredicate, RuleTest locationPredicate, BlockState state) {
        this(inputPredicate, locationPredicate, PosAlwaysTrueTest.INSTANCE, state);
    }

    public ProcessorRule(RuleTest inputPredicate, RuleTest locationPredicate, PosRuleTest positionPredicate, BlockState state) {
        this(inputPredicate, locationPredicate, positionPredicate, state, DEFAULT_BLOCK_ENTITY_MODIFIER);
    }

    public ProcessorRule(
        RuleTest inputPredicate, RuleTest locationPredicate, PosRuleTest positionPredicate, BlockState outputState, RuleBlockEntityModifier blockEntityModifier
    ) {
        this.inputPredicate = inputPredicate;
        this.locPredicate = locationPredicate;
        this.posPredicate = positionPredicate;
        this.outputState = outputState;
        this.blockEntityModifier = blockEntityModifier;
    }

    public boolean test(BlockState input, BlockState currentState, BlockPos originalPos, BlockPos currentPos, BlockPos pivot, RandomSource random) {
        return this.inputPredicate.test(input, random)
            && this.locPredicate.test(currentState, random)
            && this.posPredicate.test(originalPos, currentPos, pivot, random);
    }

    public BlockState getOutputState() {
        return this.outputState;
    }

    @Nullable
    public CompoundTag getOutputTag(RandomSource random, @Nullable CompoundTag nbt) {
        return this.blockEntityModifier.apply(random, nbt);
    }
}
