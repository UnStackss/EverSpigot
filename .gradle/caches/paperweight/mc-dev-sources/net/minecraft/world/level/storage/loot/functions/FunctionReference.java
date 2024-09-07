package net.minecraft.world.level.storage.loot.functions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

public class FunctionReference extends LootItemConditionalFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<FunctionReference> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(ResourceKey.codec(Registries.ITEM_MODIFIER).fieldOf("name").forGetter(function -> function.name))
                .apply(instance, FunctionReference::new)
    );
    private final ResourceKey<LootItemFunction> name;

    private FunctionReference(List<LootItemCondition> conditions, ResourceKey<LootItemFunction> name) {
        super(conditions);
        this.name = name;
    }

    @Override
    public LootItemFunctionType<FunctionReference> getType() {
        return LootItemFunctions.REFERENCE;
    }

    @Override
    public void validate(ValidationContext reporter) {
        if (!reporter.allowsReferences()) {
            reporter.reportProblem("Uses reference to " + this.name.location() + ", but references are not allowed");
        } else if (reporter.hasVisitedElement(this.name)) {
            reporter.reportProblem("Function " + this.name.location() + " is recursively called");
        } else {
            super.validate(reporter);
            reporter.resolver()
                .get(Registries.ITEM_MODIFIER, this.name)
                .ifPresentOrElse(
                    reference -> reference.value().validate(reporter.enterElement(".{" + this.name.location() + "}", this.name)),
                    () -> reporter.reportProblem("Unknown function table called " + this.name.location())
                );
        }
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        LootItemFunction lootItemFunction = context.getResolver().get(Registries.ITEM_MODIFIER, this.name).map(Holder::value).orElse(null);
        if (lootItemFunction == null) {
            LOGGER.warn("Unknown function: {}", this.name.location());
            return stack;
        } else {
            LootContext.VisitedEntry<?> visitedEntry = LootContext.createVisitedEntry(lootItemFunction);
            if (context.pushVisitedElement(visitedEntry)) {
                ItemStack var5;
                try {
                    var5 = lootItemFunction.apply(stack, context);
                } finally {
                    context.popVisitedElement(visitedEntry);
                }

                return var5;
            } else {
                LOGGER.warn("Detected infinite loop in loot tables");
                return stack;
            }
        }
    }

    public static LootItemConditionalFunction.Builder<?> functionReference(ResourceKey<LootItemFunction> name) {
        return simpleBuilder(conditions -> new FunctionReference(conditions, name));
    }
}
