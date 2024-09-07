package net.minecraft.server.commands.data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class DataCommands {
    private static final SimpleCommandExceptionType ERROR_MERGE_UNCHANGED = new SimpleCommandExceptionType(Component.translatable("commands.data.merge.failed"));
    private static final DynamicCommandExceptionType ERROR_GET_NOT_NUMBER = new DynamicCommandExceptionType(
        path -> Component.translatableEscape("commands.data.get.invalid", path)
    );
    private static final DynamicCommandExceptionType ERROR_GET_NON_EXISTENT = new DynamicCommandExceptionType(
        path -> Component.translatableEscape("commands.data.get.unknown", path)
    );
    private static final SimpleCommandExceptionType ERROR_MULTIPLE_TAGS = new SimpleCommandExceptionType(Component.translatable("commands.data.get.multiple"));
    private static final DynamicCommandExceptionType ERROR_EXPECTED_OBJECT = new DynamicCommandExceptionType(
        nbt -> Component.translatableEscape("commands.data.modify.expected_object", nbt)
    );
    private static final DynamicCommandExceptionType ERROR_EXPECTED_VALUE = new DynamicCommandExceptionType(
        nbt -> Component.translatableEscape("commands.data.modify.expected_value", nbt)
    );
    private static final Dynamic2CommandExceptionType ERROR_INVALID_SUBSTRING = new Dynamic2CommandExceptionType(
        (startIndex, endIndex) -> Component.translatableEscape("commands.data.modify.invalid_substring", startIndex, endIndex)
    );
    public static final List<Function<String, DataCommands.DataProvider>> ALL_PROVIDERS = ImmutableList.of(
        EntityDataAccessor.PROVIDER, BlockDataAccessor.PROVIDER, StorageDataAccessor.PROVIDER
    );
    public static final List<DataCommands.DataProvider> TARGET_PROVIDERS = ALL_PROVIDERS.stream()
        .map(factory -> factory.apply("target"))
        .collect(ImmutableList.toImmutableList());
    public static final List<DataCommands.DataProvider> SOURCE_PROVIDERS = ALL_PROVIDERS.stream()
        .map(factory -> factory.apply("source"))
        .collect(ImmutableList.toImmutableList());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("data").requires(source -> source.hasPermission(2));

        for (DataCommands.DataProvider dataProvider : TARGET_PROVIDERS) {
            literalArgumentBuilder.then(
                    dataProvider.wrap(
                        Commands.literal("merge"),
                        builder -> builder.then(
                                Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                    .executes(
                                        context -> mergeData(
                                                context.getSource(), dataProvider.access(context), CompoundTagArgument.getCompoundTag(context, "nbt")
                                            )
                                    )
                            )
                    )
                )
                .then(
                    dataProvider.wrap(
                        Commands.literal("get"),
                        builder -> builder.executes(context -> getData(context.getSource(), dataProvider.access(context)))
                                .then(
                                    Commands.argument("path", NbtPathArgument.nbtPath())
                                        .executes(
                                            context -> getData(context.getSource(), dataProvider.access(context), NbtPathArgument.getPath(context, "path"))
                                        )
                                        .then(
                                            Commands.argument("scale", DoubleArgumentType.doubleArg())
                                                .executes(
                                                    context -> getNumeric(
                                                            context.getSource(),
                                                            dataProvider.access(context),
                                                            NbtPathArgument.getPath(context, "path"),
                                                            DoubleArgumentType.getDouble(context, "scale")
                                                        )
                                                )
                                        )
                                )
                    )
                )
                .then(
                    dataProvider.wrap(
                        Commands.literal("remove"),
                        builder -> builder.then(
                                Commands.argument("path", NbtPathArgument.nbtPath())
                                    .executes(
                                        context -> removeData(context.getSource(), dataProvider.access(context), NbtPathArgument.getPath(context, "path"))
                                    )
                            )
                    )
                )
                .then(
                    decorateModification(
                        (builder, modifier) -> builder.then(
                                    Commands.literal("insert")
                                        .then(
                                            Commands.argument("index", IntegerArgumentType.integer())
                                                .then(
                                                    modifier.create(
                                                        (context, sourceNbt, path, elements) -> path.insert(
                                                                IntegerArgumentType.getInteger(context, "index"), sourceNbt, elements
                                                            )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("prepend")
                                        .then(modifier.create((context, sourceNbt, path, elements) -> path.insert(0, sourceNbt, elements)))
                                )
                                .then(
                                    Commands.literal("append")
                                        .then(modifier.create((context, sourceNbt, path, elements) -> path.insert(-1, sourceNbt, elements)))
                                )
                                .then(
                                    Commands.literal("set")
                                        .then(modifier.create((context, sourceNbt, path, elements) -> path.set(sourceNbt, Iterables.getLast(elements))))
                                )
                                .then(Commands.literal("merge").then(modifier.create((context, element, path, elements) -> {
                                    CompoundTag compoundTag = new CompoundTag();

                                    for (Tag tag : elements) {
                                        if (NbtPathArgument.NbtPath.isTooDeep(tag, 0)) {
                                            throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
                                        }

                                        if (!(tag instanceof CompoundTag compoundTag2)) {
                                            throw ERROR_EXPECTED_OBJECT.create(tag);
                                        }

                                        compoundTag.merge(compoundTag2);
                                    }

                                    Collection<Tag> collection = path.getOrCreate(element, CompoundTag::new);
                                    int i = 0;

                                    for (Tag tag2 : collection) {
                                        if (!(tag2 instanceof CompoundTag compoundTag3)) {
                                            throw ERROR_EXPECTED_OBJECT.create(tag2);
                                        }

                                        CompoundTag compoundTag5 = compoundTag3.copy();
                                        compoundTag3.merge(compoundTag);
                                        i += compoundTag5.equals(compoundTag3) ? 0 : 1;
                                    }

                                    return i;
                                })))
                    )
                );
        }

        dispatcher.register(literalArgumentBuilder);
    }

    private static String getAsText(Tag nbt) throws CommandSyntaxException {
        if (nbt.getType().isValue()) {
            return nbt.getAsString();
        } else {
            throw ERROR_EXPECTED_VALUE.create(nbt);
        }
    }

    private static List<Tag> stringifyTagList(List<Tag> list, DataCommands.StringProcessor processor) throws CommandSyntaxException {
        List<Tag> list2 = new ArrayList<>(list.size());

        for (Tag tag : list) {
            String string = getAsText(tag);
            list2.add(StringTag.valueOf(processor.process(string)));
        }

        return list2;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> decorateModification(
        BiConsumer<ArgumentBuilder<CommandSourceStack, ?>, DataCommands.DataManipulatorDecorator> subArgumentAdder
    ) {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("modify");

        for (DataCommands.DataProvider dataProvider : TARGET_PROVIDERS) {
            dataProvider.wrap(
                literalArgumentBuilder,
                builder -> {
                    ArgumentBuilder<CommandSourceStack, ?> argumentBuilder = Commands.argument("targetPath", NbtPathArgument.nbtPath());

                    for (DataCommands.DataProvider dataProvider2 : SOURCE_PROVIDERS) {
                        subArgumentAdder.accept(
                            argumentBuilder,
                            operation -> dataProvider2.wrap(
                                    Commands.literal("from"),
                                    builderx -> builderx.executes(
                                                context -> manipulateData(context, dataProvider, operation, getSingletonSource(context, dataProvider2))
                                            )
                                            .then(
                                                Commands.argument("sourcePath", NbtPathArgument.nbtPath())
                                                    .executes(
                                                        context -> manipulateData(context, dataProvider, operation, resolveSourcePath(context, dataProvider2))
                                                    )
                                            )
                                )
                        );
                        subArgumentAdder.accept(
                            argumentBuilder,
                            operation -> dataProvider2.wrap(
                                    Commands.literal("string"),
                                    builderx -> builderx.executes(
                                                context -> manipulateData(
                                                        context,
                                                        dataProvider,
                                                        operation,
                                                        stringifyTagList(getSingletonSource(context, dataProvider2), value -> value)
                                                    )
                                            )
                                            .then(
                                                Commands.argument("sourcePath", NbtPathArgument.nbtPath())
                                                    .executes(
                                                        context -> manipulateData(
                                                                context,
                                                                dataProvider,
                                                                operation,
                                                                stringifyTagList(resolveSourcePath(context, dataProvider2), value -> value)
                                                            )
                                                    )
                                                    .then(
                                                        Commands.argument("start", IntegerArgumentType.integer())
                                                            .executes(
                                                                context -> manipulateData(
                                                                        context,
                                                                        dataProvider,
                                                                        operation,
                                                                        stringifyTagList(
                                                                            resolveSourcePath(context, dataProvider2),
                                                                            value -> substring(value, IntegerArgumentType.getInteger(context, "start"))
                                                                        )
                                                                    )
                                                            )
                                                            .then(
                                                                Commands.argument("end", IntegerArgumentType.integer())
                                                                    .executes(
                                                                        context -> manipulateData(
                                                                                context,
                                                                                dataProvider,
                                                                                operation,
                                                                                stringifyTagList(
                                                                                    resolveSourcePath(context, dataProvider2),
                                                                                    value -> substring(
                                                                                            value,
                                                                                            IntegerArgumentType.getInteger(context, "start"),
                                                                                            IntegerArgumentType.getInteger(context, "end")
                                                                                        )
                                                                                )
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            )
                                )
                        );
                    }

                    subArgumentAdder.accept(
                        argumentBuilder, modifier -> Commands.literal("value").then(Commands.argument("value", NbtTagArgument.nbtTag()).executes(context -> {
                                List<Tag> list = Collections.singletonList(NbtTagArgument.getNbtTag(context, "value"));
                                return manipulateData(context, dataProvider, modifier, list);
                            }))
                    );
                    return builder.then(argumentBuilder);
                }
            );
        }

        return literalArgumentBuilder;
    }

    private static String validatedSubstring(String string, int startIndex, int endIndex) throws CommandSyntaxException {
        if (startIndex >= 0 && endIndex <= string.length() && startIndex <= endIndex) {
            return string.substring(startIndex, endIndex);
        } else {
            throw ERROR_INVALID_SUBSTRING.create(startIndex, endIndex);
        }
    }

    private static String substring(String string, int startIndex, int endIndex) throws CommandSyntaxException {
        int i = string.length();
        int j = getOffset(startIndex, i);
        int k = getOffset(endIndex, i);
        return validatedSubstring(string, j, k);
    }

    private static String substring(String string, int startIndex) throws CommandSyntaxException {
        int i = string.length();
        return validatedSubstring(string, getOffset(startIndex, i), i);
    }

    private static int getOffset(int index, int length) {
        return index >= 0 ? index : length + index;
    }

    private static List<Tag> getSingletonSource(CommandContext<CommandSourceStack> context, DataCommands.DataProvider objectType) throws CommandSyntaxException {
        DataAccessor dataAccessor = objectType.access(context);
        return Collections.singletonList(dataAccessor.getData());
    }

    private static List<Tag> resolveSourcePath(CommandContext<CommandSourceStack> context, DataCommands.DataProvider objectType) throws CommandSyntaxException {
        DataAccessor dataAccessor = objectType.access(context);
        NbtPathArgument.NbtPath nbtPath = NbtPathArgument.getPath(context, "sourcePath");
        return nbtPath.get(dataAccessor.getData());
    }

    private static int manipulateData(
        CommandContext<CommandSourceStack> context, DataCommands.DataProvider objectType, DataCommands.DataManipulator modifier, List<Tag> elements
    ) throws CommandSyntaxException {
        DataAccessor dataAccessor = objectType.access(context);
        NbtPathArgument.NbtPath nbtPath = NbtPathArgument.getPath(context, "targetPath");
        CompoundTag compoundTag = dataAccessor.getData();
        int i = modifier.modify(context, compoundTag, nbtPath, elements);
        if (i == 0) {
            throw ERROR_MERGE_UNCHANGED.create();
        } else {
            dataAccessor.setData(compoundTag);
            context.getSource().sendSuccess(() -> dataAccessor.getModifiedSuccess(), true);
            return i;
        }
    }

    private static int removeData(CommandSourceStack source, DataAccessor object, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        CompoundTag compoundTag = object.getData();
        int i = path.remove(compoundTag);
        if (i == 0) {
            throw ERROR_MERGE_UNCHANGED.create();
        } else {
            object.setData(compoundTag);
            source.sendSuccess(() -> object.getModifiedSuccess(), true);
            return i;
        }
    }

    public static Tag getSingleTag(NbtPathArgument.NbtPath path, DataAccessor object) throws CommandSyntaxException {
        Collection<Tag> collection = path.get(object.getData());
        Iterator<Tag> iterator = collection.iterator();
        Tag tag = iterator.next();
        if (iterator.hasNext()) {
            throw ERROR_MULTIPLE_TAGS.create();
        } else {
            return tag;
        }
    }

    private static int getData(CommandSourceStack source, DataAccessor object, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        Tag tag = getSingleTag(path, object);
        int i;
        if (tag instanceof NumericTag) {
            i = Mth.floor(((NumericTag)tag).getAsDouble());
        } else if (tag instanceof CollectionTag) {
            i = ((CollectionTag)tag).size();
        } else if (tag instanceof CompoundTag) {
            i = ((CompoundTag)tag).size();
        } else {
            if (!(tag instanceof StringTag)) {
                throw ERROR_GET_NON_EXISTENT.create(path.toString());
            }

            i = tag.getAsString().length();
        }

        source.sendSuccess(() -> object.getPrintSuccess(tag), false);
        return i;
    }

    private static int getNumeric(CommandSourceStack source, DataAccessor object, NbtPathArgument.NbtPath path, double scale) throws CommandSyntaxException {
        Tag tag = getSingleTag(path, object);
        if (!(tag instanceof NumericTag)) {
            throw ERROR_GET_NOT_NUMBER.create(path.toString());
        } else {
            int i = Mth.floor(((NumericTag)tag).getAsDouble() * scale);
            source.sendSuccess(() -> object.getPrintSuccess(path, scale, i), false);
            return i;
        }
    }

    private static int getData(CommandSourceStack source, DataAccessor object) throws CommandSyntaxException {
        CompoundTag compoundTag = object.getData();
        source.sendSuccess(() -> object.getPrintSuccess(compoundTag), false);
        return 1;
    }

    private static int mergeData(CommandSourceStack source, DataAccessor object, CompoundTag nbt) throws CommandSyntaxException {
        CompoundTag compoundTag = object.getData();
        if (NbtPathArgument.NbtPath.isTooDeep(nbt, 0)) {
            throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
        } else {
            CompoundTag compoundTag2 = compoundTag.copy().merge(nbt);
            if (compoundTag.equals(compoundTag2)) {
                throw ERROR_MERGE_UNCHANGED.create();
            } else {
                object.setData(compoundTag2);
                source.sendSuccess(() -> object.getModifiedSuccess(), true);
                return 1;
            }
        }
    }

    @FunctionalInterface
    interface DataManipulator {
        int modify(CommandContext<CommandSourceStack> context, CompoundTag sourceNbt, NbtPathArgument.NbtPath path, List<Tag> elements) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface DataManipulatorDecorator {
        ArgumentBuilder<CommandSourceStack, ?> create(DataCommands.DataManipulator modifier);
    }

    public interface DataProvider {
        DataAccessor access(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

        ArgumentBuilder<CommandSourceStack, ?> wrap(
            ArgumentBuilder<CommandSourceStack, ?> argument,
            Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> argumentAdder
        );
    }

    @FunctionalInterface
    interface StringProcessor {
        String process(String string) throws CommandSyntaxException;
    }
}
