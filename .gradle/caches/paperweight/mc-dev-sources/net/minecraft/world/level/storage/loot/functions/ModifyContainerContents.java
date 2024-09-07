package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulator;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulators;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ModifyContainerContents extends LootItemConditionalFunction {
    public static final MapCodec<ModifyContainerContents> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        ContainerComponentManipulators.CODEC.fieldOf("component").forGetter(lootFunction -> lootFunction.component),
                        LootItemFunctions.ROOT_CODEC.fieldOf("modifier").forGetter(lootFunction -> lootFunction.modifier)
                    )
                )
                .apply(instance, ModifyContainerContents::new)
    );
    private final ContainerComponentManipulator<?> component;
    private final LootItemFunction modifier;

    private ModifyContainerContents(List<LootItemCondition> conditions, ContainerComponentManipulator<?> component, LootItemFunction modifier) {
        super(conditions);
        this.component = component;
        this.modifier = modifier;
    }

    @Override
    public LootItemFunctionType<ModifyContainerContents> getType() {
        return LootItemFunctions.MODIFY_CONTENTS;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.isEmpty()) {
            return stack;
        } else {
            this.component.modifyItems(stack, content -> this.modifier.apply(content, context));
            return stack;
        }
    }

    @Override
    public void validate(ValidationContext reporter) {
        super.validate(reporter);
        this.modifier.validate(reporter.forChild(".modifier"));
    }
}
