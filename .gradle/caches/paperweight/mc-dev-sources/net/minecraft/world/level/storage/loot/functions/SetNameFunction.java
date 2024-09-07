package net.minecraft.world.level.storage.loot.functions;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

public class SetNameFunction extends LootItemConditionalFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<SetNameFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(function -> function.name),
                        LootContext.EntityTarget.CODEC.optionalFieldOf("entity").forGetter(function -> function.resolutionContext),
                        SetNameFunction.Target.CODEC.optionalFieldOf("target", SetNameFunction.Target.CUSTOM_NAME).forGetter(function -> function.target)
                    )
                )
                .apply(instance, SetNameFunction::new)
    );
    private final Optional<Component> name;
    private final Optional<LootContext.EntityTarget> resolutionContext;
    private final SetNameFunction.Target target;

    private SetNameFunction(
        List<LootItemCondition> conditions, Optional<Component> name, Optional<LootContext.EntityTarget> entity, SetNameFunction.Target target
    ) {
        super(conditions);
        this.name = name;
        this.resolutionContext = entity;
        this.target = target;
    }

    @Override
    public LootItemFunctionType<SetNameFunction> getType() {
        return LootItemFunctions.SET_NAME;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.resolutionContext.<Set<LootContextParam<?>>>map(entity -> Set.of(entity.getParam())).orElse(Set.of());
    }

    public static UnaryOperator<Component> createResolver(LootContext context, @Nullable LootContext.EntityTarget sourceEntity) {
        if (sourceEntity != null) {
            Entity entity = context.getParamOrNull(sourceEntity.getParam());
            if (entity != null) {
                CommandSourceStack commandSourceStack = entity.createCommandSourceStack().withPermission(2);
                return textComponent -> {
                    try {
                        return ComponentUtils.updateForEntity(commandSourceStack, textComponent, entity, 0);
                    } catch (CommandSyntaxException var4) {
                        LOGGER.warn("Failed to resolve text component", (Throwable)var4);
                        return textComponent;
                    }
                };
            }
        }

        return textComponent -> textComponent;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        this.name.ifPresent(name -> stack.set(this.target.component(), createResolver(context, this.resolutionContext.orElse(null)).apply(name)));
        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setName(Component name, SetNameFunction.Target target) {
        return simpleBuilder(conditions -> new SetNameFunction(conditions, Optional.of(name), Optional.empty(), target));
    }

    public static LootItemConditionalFunction.Builder<?> setName(Component name, SetNameFunction.Target target, LootContext.EntityTarget entity) {
        return simpleBuilder(conditions -> new SetNameFunction(conditions, Optional.of(name), Optional.of(entity), target));
    }

    public static enum Target implements StringRepresentable {
        CUSTOM_NAME("custom_name"),
        ITEM_NAME("item_name");

        public static final Codec<SetNameFunction.Target> CODEC = StringRepresentable.fromEnum(SetNameFunction.Target::values);
        private final String name;

        private Target(final String id) {
            this.name = id;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public DataComponentType<Component> component() {
            return switch (this) {
                case CUSTOM_NAME -> DataComponents.CUSTOM_NAME;
                case ITEM_NAME -> DataComponents.ITEM_NAME;
            };
        }
    }
}
