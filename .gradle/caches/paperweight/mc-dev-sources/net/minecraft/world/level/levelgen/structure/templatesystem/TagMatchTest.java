package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TagMatchTest extends RuleTest {
    public static final MapCodec<TagMatchTest> CODEC = TagKey.codec(Registries.BLOCK).fieldOf("tag").xmap(TagMatchTest::new, ruleTest -> ruleTest.tag);
    private final TagKey<Block> tag;

    public TagMatchTest(TagKey<Block> tag) {
        this.tag = tag;
    }

    @Override
    public boolean test(BlockState state, RandomSource random) {
        return state.is(this.tag);
    }

    @Override
    protected RuleTestType<?> getType() {
        return RuleTestType.TAG_TEST;
    }
}
