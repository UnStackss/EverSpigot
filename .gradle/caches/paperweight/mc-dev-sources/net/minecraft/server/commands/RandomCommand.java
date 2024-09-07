package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequences;

public class RandomCommand {
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_LARGE = new SimpleCommandExceptionType(
        Component.translatable("commands.random.error.range_too_large")
    );
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_SMALL = new SimpleCommandExceptionType(
        Component.translatable("commands.random.error.range_too_small")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("random")
                .then(drawRandomValueTree("value", false))
                .then(drawRandomValueTree("roll", true))
                .then(
                    Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(
                            Commands.literal("*")
                                .executes(context -> resetAllSequences(context.getSource()))
                                .then(
                                    Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(
                                            context -> resetAllSequencesAndSetNewDefaults(
                                                    context.getSource(), IntegerArgumentType.getInteger(context, "seed"), true, true
                                                )
                                        )
                                        .then(
                                            Commands.argument("includeWorldSeed", BoolArgumentType.bool())
                                                .executes(
                                                    context -> resetAllSequencesAndSetNewDefaults(
                                                            context.getSource(),
                                                            IntegerArgumentType.getInteger(context, "seed"),
                                                            BoolArgumentType.getBool(context, "includeWorldSeed"),
                                                            true
                                                        )
                                                )
                                                .then(
                                                    Commands.argument("includeSequenceId", BoolArgumentType.bool())
                                                        .executes(
                                                            context -> resetAllSequencesAndSetNewDefaults(
                                                                    context.getSource(),
                                                                    IntegerArgumentType.getInteger(context, "seed"),
                                                                    BoolArgumentType.getBool(context, "includeWorldSeed"),
                                                                    BoolArgumentType.getBool(context, "includeSequenceId")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.argument("sequence", ResourceLocationArgument.id())
                                .suggests(RandomCommand::suggestRandomSequence)
                                .executes(context -> resetSequence(context.getSource(), ResourceLocationArgument.getId(context, "sequence")))
                                .then(
                                    Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(
                                            context -> resetSequence(
                                                    context.getSource(),
                                                    ResourceLocationArgument.getId(context, "sequence"),
                                                    IntegerArgumentType.getInteger(context, "seed"),
                                                    true,
                                                    true
                                                )
                                        )
                                        .then(
                                            Commands.argument("includeWorldSeed", BoolArgumentType.bool())
                                                .executes(
                                                    context -> resetSequence(
                                                            context.getSource(),
                                                            ResourceLocationArgument.getId(context, "sequence"),
                                                            IntegerArgumentType.getInteger(context, "seed"),
                                                            BoolArgumentType.getBool(context, "includeWorldSeed"),
                                                            true
                                                        )
                                                )
                                                .then(
                                                    Commands.argument("includeSequenceId", BoolArgumentType.bool())
                                                        .executes(
                                                            context -> resetSequence(
                                                                    context.getSource(),
                                                                    ResourceLocationArgument.getId(context, "sequence"),
                                                                    IntegerArgumentType.getInteger(context, "seed"),
                                                                    BoolArgumentType.getBool(context, "includeWorldSeed"),
                                                                    BoolArgumentType.getBool(context, "includeSequenceId")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> drawRandomValueTree(String argumentName, boolean roll) {
        return Commands.literal(argumentName)
            .then(
                Commands.argument("range", RangeArgument.intRange())
                    .executes(context -> randomSample(context.getSource(), RangeArgument.Ints.getRange(context, "range"), null, roll))
                    .then(
                        Commands.argument("sequence", ResourceLocationArgument.id())
                            .suggests(RandomCommand::suggestRandomSequence)
                            .requires(source -> source.hasPermission(2))
                            .executes(
                                context -> randomSample(
                                        context.getSource(),
                                        RangeArgument.Ints.getRange(context, "range"),
                                        ResourceLocationArgument.getId(context, "sequence"),
                                        roll
                                    )
                            )
                    )
            );
    }

    private static CompletableFuture<Suggestions> suggestRandomSequence(CommandContext<CommandSourceStack> context, SuggestionsBuilder suggestionsBuilder) {
        List<String> list = Lists.newArrayList();
        context.getSource().getLevel().getRandomSequences().forAllSequences((id, sequence) -> list.add(id.toString()));
        return SharedSuggestionProvider.suggest(list, suggestionsBuilder);
    }

    private static int randomSample(CommandSourceStack source, MinMaxBounds.Ints range, @Nullable ResourceLocation sequenceId, boolean roll) throws CommandSyntaxException {
        RandomSource randomSource;
        if (sequenceId != null) {
            randomSource = source.getLevel().getRandomSequence(sequenceId);
        } else {
            randomSource = source.getLevel().getRandom();
        }

        int i = range.min().orElse(Integer.MIN_VALUE);
        int j = range.max().orElse(Integer.MAX_VALUE);
        long l = (long)j - (long)i;
        if (l == 0L) {
            throw ERROR_RANGE_TOO_SMALL.create();
        } else if (l >= 2147483647L) {
            throw ERROR_RANGE_TOO_LARGE.create();
        } else {
            int k = Mth.randomBetweenInclusive(randomSource, i, j);
            if (roll) {
                source.getServer()
                    .getPlayerList()
                    .broadcastSystemMessage(Component.translatable("commands.random.roll", source.getDisplayName(), k, i, j), false);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.random.sample.success", k), false);
            }

            return k;
        }
    }

    private static int resetSequence(CommandSourceStack source, ResourceLocation sequenceId) throws CommandSyntaxException {
        source.getLevel().getRandomSequences().reset(sequenceId);
        source.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(sequenceId)), false);
        return 1;
    }

    private static int resetSequence(CommandSourceStack source, ResourceLocation sequenceId, int salt, boolean includeWorldSeed, boolean includeSequenceId) throws CommandSyntaxException {
        source.getLevel().getRandomSequences().reset(sequenceId, salt, includeWorldSeed, includeSequenceId);
        source.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(sequenceId)), false);
        return 1;
    }

    private static int resetAllSequences(CommandSourceStack source) {
        int i = source.getLevel().getRandomSequences().clear();
        source.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", i), false);
        return i;
    }

    private static int resetAllSequencesAndSetNewDefaults(CommandSourceStack source, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        RandomSequences randomSequences = source.getLevel().getRandomSequences();
        randomSequences.setSeedDefaults(salt, includeWorldSeed, includeSequenceId);
        int i = randomSequences.clear();
        source.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", i), false);
        return i;
    }
}
