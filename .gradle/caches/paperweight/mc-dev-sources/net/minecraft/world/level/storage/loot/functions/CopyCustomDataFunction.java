package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider;
import net.minecraft.world.level.storage.loot.providers.nbt.NbtProvider;
import net.minecraft.world.level.storage.loot.providers.nbt.NbtProviders;
import org.apache.commons.lang3.mutable.MutableObject;

public class CopyCustomDataFunction extends LootItemConditionalFunction {
    public static final MapCodec<CopyCustomDataFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        NbtProviders.CODEC.fieldOf("source").forGetter(function -> function.source),
                        CopyCustomDataFunction.CopyOperation.CODEC.listOf().fieldOf("ops").forGetter(function -> function.operations)
                    )
                )
                .apply(instance, CopyCustomDataFunction::new)
    );
    private final NbtProvider source;
    private final List<CopyCustomDataFunction.CopyOperation> operations;

    CopyCustomDataFunction(List<LootItemCondition> conditions, NbtProvider source, List<CopyCustomDataFunction.CopyOperation> operations) {
        super(conditions);
        this.source = source;
        this.operations = List.copyOf(operations);
    }

    @Override
    public LootItemFunctionType<CopyCustomDataFunction> getType() {
        return LootItemFunctions.COPY_CUSTOM_DATA;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.source.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        Tag tag = this.source.get(context);
        if (tag == null) {
            return stack;
        } else {
            MutableObject<CompoundTag> mutableObject = new MutableObject<>();
            Supplier<Tag> supplier = () -> {
                if (mutableObject.getValue() == null) {
                    mutableObject.setValue(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
                }

                return mutableObject.getValue();
            };
            this.operations.forEach(operation -> operation.apply(supplier, tag));
            CompoundTag compoundTag = mutableObject.getValue();
            if (compoundTag != null) {
                CustomData.set(DataComponents.CUSTOM_DATA, stack, compoundTag);
            }

            return stack;
        }
    }

    @Deprecated
    public static CopyCustomDataFunction.Builder copyData(NbtProvider source) {
        return new CopyCustomDataFunction.Builder(source);
    }

    public static CopyCustomDataFunction.Builder copyData(LootContext.EntityTarget target) {
        return new CopyCustomDataFunction.Builder(ContextNbtProvider.forContextEntity(target));
    }

    public static class Builder extends LootItemConditionalFunction.Builder<CopyCustomDataFunction.Builder> {
        private final NbtProvider source;
        private final List<CopyCustomDataFunction.CopyOperation> ops = Lists.newArrayList();

        Builder(NbtProvider source) {
            this.source = source;
        }

        public CopyCustomDataFunction.Builder copy(String source, String target, CopyCustomDataFunction.MergeStrategy operator) {
            try {
                this.ops.add(new CopyCustomDataFunction.CopyOperation(NbtPathArgument.NbtPath.of(source), NbtPathArgument.NbtPath.of(target), operator));
                return this;
            } catch (CommandSyntaxException var5) {
                throw new IllegalArgumentException(var5);
            }
        }

        public CopyCustomDataFunction.Builder copy(String source, String target) {
            return this.copy(source, target, CopyCustomDataFunction.MergeStrategy.REPLACE);
        }

        @Override
        protected CopyCustomDataFunction.Builder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new CopyCustomDataFunction(this.getConditions(), this.source, this.ops);
        }
    }

    static record CopyOperation(NbtPathArgument.NbtPath sourcePath, NbtPathArgument.NbtPath targetPath, CopyCustomDataFunction.MergeStrategy op) {
        public static final Codec<CopyCustomDataFunction.CopyOperation> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        NbtPathArgument.NbtPath.CODEC.fieldOf("source").forGetter(CopyCustomDataFunction.CopyOperation::sourcePath),
                        NbtPathArgument.NbtPath.CODEC.fieldOf("target").forGetter(CopyCustomDataFunction.CopyOperation::targetPath),
                        CopyCustomDataFunction.MergeStrategy.CODEC.fieldOf("op").forGetter(CopyCustomDataFunction.CopyOperation::op)
                    )
                    .apply(instance, CopyCustomDataFunction.CopyOperation::new)
        );

        public void apply(Supplier<Tag> itemNbtGetter, Tag sourceEntityNbt) {
            try {
                List<Tag> list = this.sourcePath.get(sourceEntityNbt);
                if (!list.isEmpty()) {
                    this.op.merge(itemNbtGetter.get(), this.targetPath, list);
                }
            } catch (CommandSyntaxException var4) {
            }
        }
    }

    public static enum MergeStrategy implements StringRepresentable {
        REPLACE("replace") {
            @Override
            public void merge(Tag itemNbt, NbtPathArgument.NbtPath targetPath, List<Tag> sourceNbts) throws CommandSyntaxException {
                targetPath.set(itemNbt, Iterables.getLast(sourceNbts));
            }
        },
        APPEND("append") {
            @Override
            public void merge(Tag itemNbt, NbtPathArgument.NbtPath targetPath, List<Tag> sourceNbts) throws CommandSyntaxException {
                List<Tag> list = targetPath.getOrCreate(itemNbt, ListTag::new);
                list.forEach(foundNbt -> {
                    if (foundNbt instanceof ListTag) {
                        sourceNbts.forEach(sourceNbt -> ((ListTag)foundNbt).add(sourceNbt.copy()));
                    }
                });
            }
        },
        MERGE("merge") {
            @Override
            public void merge(Tag itemNbt, NbtPathArgument.NbtPath targetPath, List<Tag> sourceNbts) throws CommandSyntaxException {
                List<Tag> list = targetPath.getOrCreate(itemNbt, CompoundTag::new);
                list.forEach(foundNbt -> {
                    if (foundNbt instanceof CompoundTag) {
                        sourceNbts.forEach(sourceNbt -> {
                            if (sourceNbt instanceof CompoundTag) {
                                ((CompoundTag)foundNbt).merge((CompoundTag)sourceNbt);
                            }
                        });
                    }
                });
            }
        };

        public static final Codec<CopyCustomDataFunction.MergeStrategy> CODEC = StringRepresentable.fromEnum(CopyCustomDataFunction.MergeStrategy::values);
        private final String name;

        public abstract void merge(Tag itemNbt, NbtPathArgument.NbtPath targetPath, List<Tag> sourceNbts) throws CommandSyntaxException;

        MergeStrategy(final String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
