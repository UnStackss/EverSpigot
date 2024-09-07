package net.minecraft.server.commands;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.HeightmapTypeArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.SlotsArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.SwizzleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import net.minecraft.commands.execution.tasks.IsolatedCall;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Attackable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

public class ExecuteCommand {
    private static final int MAX_TEST_AREA = 32768;
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType(
        (maxCount, count) -> Component.translatableEscape("commands.execute.blocks.toobig", maxCount, count)
    );
    private static final SimpleCommandExceptionType ERROR_CONDITIONAL_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.execute.conditional.fail")
    );
    private static final DynamicCommandExceptionType ERROR_CONDITIONAL_FAILED_COUNT = new DynamicCommandExceptionType(
        count -> Component.translatableEscape("commands.execute.conditional.fail_count", count)
    );
    @VisibleForTesting
    public static final Dynamic2CommandExceptionType ERROR_FUNCTION_CONDITION_INSTANTATION_FAILURE = new Dynamic2CommandExceptionType(
        (function, message) -> Component.translatableEscape("commands.execute.function.instantiationFailure", function, message)
    );
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PREDICATE = (context, builder) -> {
        ReloadableServerRegistries.Holder holder = context.getSource().getServer().reloadableRegistries();
        return SharedSuggestionProvider.suggestResource(holder.getKeys(Registries.PREDICATE), builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandRegistryAccess) {
        LiteralCommandNode<CommandSourceStack> literalCommandNode = dispatcher.register(Commands.literal("execute").requires(source -> source.hasPermission(2)));
        dispatcher.register(
            Commands.literal("execute")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("run").redirect(dispatcher.getRoot()))
                .then(addConditionals(literalCommandNode, Commands.literal("if"), true, commandRegistryAccess))
                .then(addConditionals(literalCommandNode, Commands.literal("unless"), false, commandRegistryAccess))
                .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalCommandNode, context -> {
                    List<CommandSourceStack> list = Lists.newArrayList();

                    for (Entity entity : EntityArgument.getOptionalEntities(context, "targets")) {
                        list.add(context.getSource().withEntity(entity));
                    }

                    return list;
                })))
                .then(Commands.literal("at").then(Commands.argument("targets", EntityArgument.entities()).fork(literalCommandNode, context -> {
                    List<CommandSourceStack> list = Lists.newArrayList();

                    for (Entity entity : EntityArgument.getOptionalEntities(context, "targets")) {
                        list.add(
                            context.getSource().withLevel((ServerLevel)entity.level()).withPosition(entity.position()).withRotation(entity.getRotationVector())
                        );
                    }

                    return list;
                })))
                .then(
                    Commands.literal("store")
                        .then(wrapStores(literalCommandNode, Commands.literal("result"), true))
                        .then(wrapStores(literalCommandNode, Commands.literal("success"), false))
                )
                .then(
                    Commands.literal("positioned")
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .redirect(
                                    literalCommandNode,
                                    context -> context.getSource()
                                            .withPosition(Vec3Argument.getVec3(context, "pos"))
                                            .withAnchor(EntityAnchorArgument.Anchor.FEET)
                                )
                        )
                        .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalCommandNode, context -> {
                            List<CommandSourceStack> list = Lists.newArrayList();

                            for (Entity entity : EntityArgument.getOptionalEntities(context, "targets")) {
                                list.add(context.getSource().withPosition(entity.position()));
                            }

                            return list;
                        })))
                        .then(
                            Commands.literal("over")
                                .then(Commands.argument("heightmap", HeightmapTypeArgument.heightmap()).redirect(literalCommandNode, context -> {
                                    Vec3 vec3 = context.getSource().getPosition();
                                    ServerLevel serverLevel = context.getSource().getLevel();
                                    double d = vec3.x();
                                    double e = vec3.z();
                                    if (!serverLevel.hasChunk(SectionPos.blockToSectionCoord(d), SectionPos.blockToSectionCoord(e))) {
                                        throw BlockPosArgument.ERROR_NOT_LOADED.create();
                                    } else {
                                        int i = serverLevel.getHeight(HeightmapTypeArgument.getHeightmap(context, "heightmap"), Mth.floor(d), Mth.floor(e));
                                        return context.getSource().withPosition(new Vec3(d, (double)i, e));
                                    }
                                }))
                        )
                )
                .then(
                    Commands.literal("rotated")
                        .then(
                            Commands.argument("rot", RotationArgument.rotation())
                                .redirect(
                                    literalCommandNode,
                                    context -> context.getSource().withRotation(RotationArgument.getRotation(context, "rot").getRotation(context.getSource()))
                                )
                        )
                        .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalCommandNode, context -> {
                            List<CommandSourceStack> list = Lists.newArrayList();

                            for (Entity entity : EntityArgument.getOptionalEntities(context, "targets")) {
                                list.add(context.getSource().withRotation(entity.getRotationVector()));
                            }

                            return list;
                        })))
                )
                .then(
                    Commands.literal("facing")
                        .then(
                            Commands.literal("entity")
                                .then(
                                    Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("anchor", EntityAnchorArgument.anchor()).fork(literalCommandNode, context -> {
                                            List<CommandSourceStack> list = Lists.newArrayList();
                                            EntityAnchorArgument.Anchor anchor = EntityAnchorArgument.getAnchor(context, "anchor");

                                            for (Entity entity : EntityArgument.getOptionalEntities(context, "targets")) {
                                                list.add(context.getSource().facing(entity, anchor));
                                            }

                                            return list;
                                        }))
                                )
                        )
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .redirect(literalCommandNode, context -> context.getSource().facing(Vec3Argument.getVec3(context, "pos")))
                        )
                )
                .then(
                    Commands.literal("align")
                        .then(
                            Commands.argument("axes", SwizzleArgument.swizzle())
                                .redirect(
                                    literalCommandNode,
                                    context -> context.getSource()
                                            .withPosition(context.getSource().getPosition().align(SwizzleArgument.getSwizzle(context, "axes")))
                                )
                        )
                )
                .then(
                    Commands.literal("anchored")
                        .then(
                            Commands.argument("anchor", EntityAnchorArgument.anchor())
                                .redirect(literalCommandNode, context -> context.getSource().withAnchor(EntityAnchorArgument.getAnchor(context, "anchor")))
                        )
                )
                .then(
                    Commands.literal("in")
                        .then(
                            Commands.argument("dimension", DimensionArgument.dimension())
                                .redirect(literalCommandNode, context -> context.getSource().withLevel(DimensionArgument.getDimension(context, "dimension")))
                        )
                )
                .then(
                    Commands.literal("summon")
                        .then(
                            Commands.argument("entity", ResourceArgument.resource(commandRegistryAccess, Registries.ENTITY_TYPE))
                                .suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                                .redirect(
                                    literalCommandNode,
                                    context -> spawnEntityAndRedirect(context.getSource(), ResourceArgument.getSummonableEntityType(context, "entity"))
                                )
                        )
                )
                .then(createRelationOperations(literalCommandNode, Commands.literal("on")))
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapStores(
        LiteralCommandNode<CommandSourceStack> node, LiteralArgumentBuilder<CommandSourceStack> builder, boolean requestResult
    ) {
        builder.then(
            Commands.literal("score")
                .then(
                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                        .then(
                            Commands.argument("objective", ObjectiveArgument.objective())
                                .redirect(
                                    node,
                                    context -> storeValue(
                                            context.getSource(),
                                            ScoreHolderArgument.getNamesWithDefaultWildcard(context, "targets"),
                                            ObjectiveArgument.getObjective(context, "objective"),
                                            requestResult
                                        )
                                )
                        )
                )
        );
        builder.then(
            Commands.literal("bossbar")
                .then(
                    Commands.argument("id", ResourceLocationArgument.id())
                        .suggests(BossBarCommands.SUGGEST_BOSS_BAR)
                        .then(
                            Commands.literal("value")
                                .redirect(node, context -> storeValue(context.getSource(), BossBarCommands.getBossBar(context), true, requestResult))
                        )
                        .then(
                            Commands.literal("max")
                                .redirect(node, context -> storeValue(context.getSource(), BossBarCommands.getBossBar(context), false, requestResult))
                        )
                )
        );

        for (DataCommands.DataProvider dataProvider : DataCommands.TARGET_PROVIDERS) {
            dataProvider.wrap(
                builder,
                builderx -> builderx.then(
                        Commands.argument("path", NbtPathArgument.nbtPath())
                            .then(
                                Commands.literal("int")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                node,
                                                context -> storeData(
                                                        context.getSource(),
                                                        dataProvider.access(context),
                                                        NbtPathArgument.getPath(context, "path"),
                                                        result -> IntTag.valueOf((int)((double)result * DoubleArgumentType.getDouble(context, "scale"))),
                                                        requestResult
                                                    )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("float")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                node,
                                                context -> storeData(
                                                        context.getSource(),
                                                        dataProvider.access(context),
                                                        NbtPathArgument.getPath(context, "path"),
                                                        result -> FloatTag.valueOf((float)((double)result * DoubleArgumentType.getDouble(context, "scale"))),
                                                        requestResult
                                                    )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("short")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                node,
                                                context -> storeData(
                                                        context.getSource(),
                                                        dataProvider.access(context),
                                                        NbtPathArgument.getPath(context, "path"),
                                                        result -> ShortTag.valueOf(
                                                                (short)((int)((double)result * DoubleArgumentType.getDouble(context, "scale")))
                                                            ),
                                                        requestResult
                                                    )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("long")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                node,
                                                context -> storeData(
                                                        context.getSource(),
                                                        dataProvider.access(context),
                                                        NbtPathArgument.getPath(context, "path"),
                                                        result -> LongTag.valueOf((long)((double)result * DoubleArgumentType.getDouble(context, "scale"))),
                                                        requestResult
                                                    )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("double")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                node,
                                                context -> storeData(
                                                        context.getSource(),
                                                        dataProvider.access(context),
                                                        NbtPathArgument.getPath(context, "path"),
                                                        result -> DoubleTag.valueOf((double)result * DoubleArgumentType.getDouble(context, "scale")),
                                                        requestResult
                                                    )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("byte")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                node,
                                                context -> storeData(
                                                        context.getSource(),
                                                        dataProvider.access(context),
                                                        NbtPathArgument.getPath(context, "path"),
                                                        result -> ByteTag.valueOf(
                                                                (byte)((int)((double)result * DoubleArgumentType.getDouble(context, "scale")))
                                                            ),
                                                        requestResult
                                                    )
                                            )
                                    )
                            )
                    )
            );
        }

        return builder;
    }

    private static CommandSourceStack storeValue(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, boolean requestResult) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        return source.withCallback((successful, returnValue) -> {
            for (ScoreHolder scoreHolder : targets) {
                ScoreAccess scoreAccess = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
                int i = requestResult ? returnValue : (successful ? 1 : 0);
                scoreAccess.set(i);
            }
        }, CommandResultCallback::chain);
    }

    private static CommandSourceStack storeValue(CommandSourceStack source, CustomBossEvent bossBar, boolean storeInValue, boolean requestResult) {
        return source.withCallback((successful, returnValue) -> {
            int i = requestResult ? returnValue : (successful ? 1 : 0);
            if (storeInValue) {
                bossBar.setValue(i);
            } else {
                bossBar.setMax(i);
            }
        }, CommandResultCallback::chain);
    }

    private static CommandSourceStack storeData(
        CommandSourceStack source, DataAccessor object, NbtPathArgument.NbtPath path, IntFunction<Tag> nbtSetter, boolean requestResult
    ) {
        return source.withCallback((successful, returnValue) -> {
            try {
                CompoundTag compoundTag = object.getData();
                int i = requestResult ? returnValue : (successful ? 1 : 0);
                path.set(compoundTag, nbtSetter.apply(i));
                object.setData(compoundTag);
            } catch (CommandSyntaxException var8) {
            }
        }, CommandResultCallback::chain);
    }

    private static boolean isChunkLoaded(ServerLevel world, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        LevelChunk levelChunk = world.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        return levelChunk != null && levelChunk.getFullStatus() == FullChunkStatus.ENTITY_TICKING && world.areEntitiesLoaded(chunkPos.toLong());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addConditionals(
        CommandNode<CommandSourceStack> root,
        LiteralArgumentBuilder<CommandSourceStack> argumentBuilder,
        boolean positive,
        CommandBuildContext commandRegistryAccess
    ) {
        argumentBuilder.then(
                Commands.literal("block")
                    .then(
                        Commands.argument("pos", BlockPosArgument.blockPos())
                            .then(
                                addConditional(
                                    root,
                                    Commands.argument("block", BlockPredicateArgument.blockPredicate(commandRegistryAccess)),
                                    positive,
                                    context -> BlockPredicateArgument.getBlockPredicate(context, "block")
                                            .test(new BlockInWorld(context.getSource().getLevel(), BlockPosArgument.getLoadedBlockPos(context, "pos"), true))
                                )
                            )
                    )
            )
            .then(
                Commands.literal("biome")
                    .then(
                        Commands.argument("pos", BlockPosArgument.blockPos())
                            .then(
                                addConditional(
                                    root,
                                    Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(commandRegistryAccess, Registries.BIOME)),
                                    positive,
                                    context -> ResourceOrTagArgument.getResourceOrTag(context, "biome", Registries.BIOME)
                                            .test(context.getSource().getLevel().getBiome(BlockPosArgument.getLoadedBlockPos(context, "pos")))
                                )
                            )
                    )
            )
            .then(
                Commands.literal("loaded")
                    .then(
                        addConditional(
                            root,
                            Commands.argument("pos", BlockPosArgument.blockPos()),
                            positive,
                            commandContext -> isChunkLoaded(commandContext.getSource().getLevel(), BlockPosArgument.getBlockPos(commandContext, "pos"))
                        )
                    )
            )
            .then(
                Commands.literal("dimension")
                    .then(
                        addConditional(
                            root,
                            Commands.argument("dimension", DimensionArgument.dimension()),
                            positive,
                            context -> DimensionArgument.getDimension(context, "dimension") == context.getSource().getLevel()
                        )
                    )
            )
            .then(
                Commands.literal("score")
                    .then(
                        Commands.argument("target", ScoreHolderArgument.scoreHolder())
                            .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                            .then(
                                Commands.argument("targetObjective", ObjectiveArgument.objective())
                                    .then(
                                        Commands.literal("=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            root,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            positive,
                                                            context -> checkScore(context, (targetScore, sourceScore) -> targetScore == sourceScore)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("<")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            root,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            positive,
                                                            context -> checkScore(context, (targetScore, sourceScore) -> targetScore < sourceScore)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("<=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            root,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            positive,
                                                            context -> checkScore(context, (targetScore, sourceScore) -> targetScore <= sourceScore)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal(">")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            root,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            positive,
                                                            context -> checkScore(context, (targetScore, sourceScore) -> targetScore > sourceScore)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal(">=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            root,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            positive,
                                                            context -> checkScore(context, (targetScore, sourceScore) -> targetScore >= sourceScore)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("matches")
                                            .then(
                                                addConditional(
                                                    root,
                                                    Commands.argument("range", RangeArgument.intRange()),
                                                    positive,
                                                    context -> checkScore(context, RangeArgument.Ints.getRange(context, "range"))
                                                )
                                            )
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("blocks")
                    .then(
                        Commands.argument("start", BlockPosArgument.blockPos())
                            .then(
                                Commands.argument("end", BlockPosArgument.blockPos())
                                    .then(
                                        Commands.argument("destination", BlockPosArgument.blockPos())
                                            .then(addIfBlocksConditional(root, Commands.literal("all"), positive, false))
                                            .then(addIfBlocksConditional(root, Commands.literal("masked"), positive, true))
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("entity")
                    .then(
                        Commands.argument("entities", EntityArgument.entities())
                            .fork(root, context -> expect(context, positive, !EntityArgument.getOptionalEntities(context, "entities").isEmpty()))
                            .executes(createNumericConditionalHandler(positive, context -> EntityArgument.getOptionalEntities(context, "entities").size()))
                    )
            )
            .then(
                Commands.literal("predicate")
                    .then(
                        addConditional(
                            root,
                            Commands.argument("predicate", ResourceOrIdArgument.lootPredicate(commandRegistryAccess)).suggests(SUGGEST_PREDICATE),
                            positive,
                            context -> checkCustomPredicate(context.getSource(), ResourceOrIdArgument.getLootPredicate(context, "predicate"))
                        )
                    )
            )
            .then(
                Commands.literal("function")
                    .then(
                        Commands.argument("name", FunctionArgument.functions())
                            .suggests(FunctionCommand.SUGGEST_FUNCTION)
                            .fork(root, new ExecuteCommand.ExecuteIfFunctionCustomModifier(positive))
                    )
            )
            .then(
                Commands.literal("items")
                    .then(
                        Commands.literal("entity")
                            .then(
                                Commands.argument("entities", EntityArgument.entities())
                                    .then(
                                        Commands.argument("slots", SlotsArgument.slots())
                                            .then(
                                                Commands.argument("item_predicate", ItemPredicateArgument.itemPredicate(commandRegistryAccess))
                                                    .fork(
                                                        root,
                                                        commandContext -> expect(
                                                                commandContext,
                                                                positive,
                                                                countItems(
                                                                        EntityArgument.getEntities(commandContext, "entities"),
                                                                        SlotsArgument.getSlots(commandContext, "slots"),
                                                                        ItemPredicateArgument.getItemPredicate(commandContext, "item_predicate")
                                                                    )
                                                                    > 0
                                                            )
                                                    )
                                                    .executes(
                                                        createNumericConditionalHandler(
                                                            positive,
                                                            commandContext -> countItems(
                                                                    EntityArgument.getEntities(commandContext, "entities"),
                                                                    SlotsArgument.getSlots(commandContext, "slots"),
                                                                    ItemPredicateArgument.getItemPredicate(commandContext, "item_predicate")
                                                                )
                                                        )
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("block")
                            .then(
                                Commands.argument("pos", BlockPosArgument.blockPos())
                                    .then(
                                        Commands.argument("slots", SlotsArgument.slots())
                                            .then(
                                                Commands.argument("item_predicate", ItemPredicateArgument.itemPredicate(commandRegistryAccess))
                                                    .fork(
                                                        root,
                                                        commandContext -> expect(
                                                                commandContext,
                                                                positive,
                                                                countItems(
                                                                        commandContext.getSource(),
                                                                        BlockPosArgument.getLoadedBlockPos(commandContext, "pos"),
                                                                        SlotsArgument.getSlots(commandContext, "slots"),
                                                                        ItemPredicateArgument.getItemPredicate(commandContext, "item_predicate")
                                                                    )
                                                                    > 0
                                                            )
                                                    )
                                                    .executes(
                                                        createNumericConditionalHandler(
                                                            positive,
                                                            commandContext -> countItems(
                                                                    commandContext.getSource(),
                                                                    BlockPosArgument.getLoadedBlockPos(commandContext, "pos"),
                                                                    SlotsArgument.getSlots(commandContext, "slots"),
                                                                    ItemPredicateArgument.getItemPredicate(commandContext, "item_predicate")
                                                                )
                                                        )
                                                    )
                                            )
                                    )
                            )
                    )
            );

        for (DataCommands.DataProvider dataProvider : DataCommands.SOURCE_PROVIDERS) {
            argumentBuilder.then(
                dataProvider.wrap(
                    Commands.literal("data"),
                    builder -> builder.then(
                            Commands.argument("path", NbtPathArgument.nbtPath())
                                .fork(
                                    root,
                                    context -> expect(
                                            context, positive, checkMatchingData(dataProvider.access(context), NbtPathArgument.getPath(context, "path")) > 0
                                        )
                                )
                                .executes(
                                    createNumericConditionalHandler(
                                        positive, context -> checkMatchingData(dataProvider.access(context), NbtPathArgument.getPath(context, "path"))
                                    )
                                )
                        )
                )
            );
        }

        return argumentBuilder;
    }

    private static int countItems(Iterable<? extends Entity> entities, SlotRange slotRange, Predicate<ItemStack> predicate) {
        int i = 0;

        for (Entity entity : entities) {
            IntList intList = slotRange.slots();

            for (int j = 0; j < intList.size(); j++) {
                int k = intList.getInt(j);
                SlotAccess slotAccess = entity.getSlot(k);
                ItemStack itemStack = slotAccess.get();
                if (predicate.test(itemStack)) {
                    i += itemStack.getCount();
                }
            }
        }

        return i;
    }

    private static int countItems(CommandSourceStack source, BlockPos pos, SlotRange slotRange, Predicate<ItemStack> predicate) throws CommandSyntaxException {
        int i = 0;
        Container container = ItemCommands.getContainer(source, pos, ItemCommands.ERROR_SOURCE_NOT_A_CONTAINER);
        int j = container.getContainerSize();
        IntList intList = slotRange.slots();

        for (int k = 0; k < intList.size(); k++) {
            int l = intList.getInt(k);
            if (l >= 0 && l < j) {
                ItemStack itemStack = container.getItem(l);
                if (predicate.test(itemStack)) {
                    i += itemStack.getCount();
                }
            }
        }

        return i;
    }

    private static Command<CommandSourceStack> createNumericConditionalHandler(boolean positive, ExecuteCommand.CommandNumericPredicate condition) {
        return positive ? context -> {
            int i = condition.test(context);
            if (i > 0) {
                context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass_count", i), false);
                return i;
            } else {
                throw ERROR_CONDITIONAL_FAILED.create();
            }
        } : context -> {
            int i = condition.test(context);
            if (i == 0) {
                context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
                return 1;
            } else {
                throw ERROR_CONDITIONAL_FAILED_COUNT.create(i);
            }
        };
    }

    private static int checkMatchingData(DataAccessor object, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        return path.countMatching(object.getData());
    }

    private static boolean checkScore(CommandContext<CommandSourceStack> context, ExecuteCommand.IntBiPredicate predicate) throws CommandSyntaxException {
        ScoreHolder scoreHolder = ScoreHolderArgument.getName(context, "target");
        Objective objective = ObjectiveArgument.getObjective(context, "targetObjective");
        ScoreHolder scoreHolder2 = ScoreHolderArgument.getName(context, "source");
        Objective objective2 = ObjectiveArgument.getObjective(context, "sourceObjective");
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
        ReadOnlyScoreInfo readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
        ReadOnlyScoreInfo readOnlyScoreInfo2 = scoreboard.getPlayerScoreInfo(scoreHolder2, objective2);
        return readOnlyScoreInfo != null && readOnlyScoreInfo2 != null && predicate.test(readOnlyScoreInfo.value(), readOnlyScoreInfo2.value());
    }

    private static boolean checkScore(CommandContext<CommandSourceStack> context, MinMaxBounds.Ints range) throws CommandSyntaxException {
        ScoreHolder scoreHolder = ScoreHolderArgument.getName(context, "target");
        Objective objective = ObjectiveArgument.getObjective(context, "targetObjective");
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
        ReadOnlyScoreInfo readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
        return readOnlyScoreInfo != null && range.matches(readOnlyScoreInfo.value());
    }

    private static boolean checkCustomPredicate(CommandSourceStack source, Holder<LootItemCondition> lootCondition) {
        ServerLevel serverLevel = source.getLevel();
        LootParams lootParams = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.ORIGIN, source.getPosition())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, source.getEntity())
            .create(LootContextParamSets.COMMAND);
        LootContext lootContext = new LootContext.Builder(lootParams).create(Optional.empty());
        lootContext.pushVisitedElement(LootContext.createVisitedEntry(lootCondition.value()));
        return lootCondition.value().test(lootContext);
    }

    private static Collection<CommandSourceStack> expect(CommandContext<CommandSourceStack> context, boolean positive, boolean value) {
        return (Collection<CommandSourceStack>)(value == positive ? Collections.singleton(context.getSource()) : Collections.emptyList());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addConditional(
        CommandNode<CommandSourceStack> root, ArgumentBuilder<CommandSourceStack, ?> builder, boolean positive, ExecuteCommand.CommandPredicate condition
    ) {
        return builder.fork(root, context -> expect(context, positive, condition.test(context))).executes(context -> {
            if (positive == condition.test(context)) {
                context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
                return 1;
            } else {
                throw ERROR_CONDITIONAL_FAILED.create();
            }
        });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addIfBlocksConditional(
        CommandNode<CommandSourceStack> root, ArgumentBuilder<CommandSourceStack, ?> builder, boolean positive, boolean masked
    ) {
        return builder.fork(root, context -> expect(context, positive, checkRegions(context, masked).isPresent()))
            .executes(positive ? context -> checkIfRegions(context, masked) : context -> checkUnlessRegions(context, masked));
    }

    private static int checkIfRegions(CommandContext<CommandSourceStack> context, boolean masked) throws CommandSyntaxException {
        OptionalInt optionalInt = checkRegions(context, masked);
        if (optionalInt.isPresent()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass_count", optionalInt.getAsInt()), false);
            return optionalInt.getAsInt();
        } else {
            throw ERROR_CONDITIONAL_FAILED.create();
        }
    }

    private static int checkUnlessRegions(CommandContext<CommandSourceStack> context, boolean masked) throws CommandSyntaxException {
        OptionalInt optionalInt = checkRegions(context, masked);
        if (optionalInt.isPresent()) {
            throw ERROR_CONDITIONAL_FAILED_COUNT.create(optionalInt.getAsInt());
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
            return 1;
        }
    }

    private static OptionalInt checkRegions(CommandContext<CommandSourceStack> context, boolean masked) throws CommandSyntaxException {
        return checkRegions(
            context.getSource().getLevel(),
            BlockPosArgument.getLoadedBlockPos(context, "start"),
            BlockPosArgument.getLoadedBlockPos(context, "end"),
            BlockPosArgument.getLoadedBlockPos(context, "destination"),
            masked
        );
    }

    private static OptionalInt checkRegions(ServerLevel world, BlockPos start, BlockPos end, BlockPos destination, boolean masked) throws CommandSyntaxException {
        BoundingBox boundingBox = BoundingBox.fromCorners(start, end);
        BoundingBox boundingBox2 = BoundingBox.fromCorners(destination, destination.offset(boundingBox.getLength()));
        BlockPos blockPos = new BlockPos(
            boundingBox2.minX() - boundingBox.minX(), boundingBox2.minY() - boundingBox.minY(), boundingBox2.minZ() - boundingBox.minZ()
        );
        int i = boundingBox.getXSpan() * boundingBox.getYSpan() * boundingBox.getZSpan();
        if (i > 32768) {
            throw ERROR_AREA_TOO_LARGE.create(32768, i);
        } else {
            RegistryAccess registryAccess = world.registryAccess();
            int j = 0;

            for (int k = boundingBox.minZ(); k <= boundingBox.maxZ(); k++) {
                for (int l = boundingBox.minY(); l <= boundingBox.maxY(); l++) {
                    for (int m = boundingBox.minX(); m <= boundingBox.maxX(); m++) {
                        BlockPos blockPos2 = new BlockPos(m, l, k);
                        BlockPos blockPos3 = blockPos2.offset(blockPos);
                        BlockState blockState = world.getBlockState(blockPos2);
                        if (!masked || !blockState.is(Blocks.AIR)) {
                            if (blockState != world.getBlockState(blockPos3)) {
                                return OptionalInt.empty();
                            }

                            BlockEntity blockEntity = world.getBlockEntity(blockPos2);
                            BlockEntity blockEntity2 = world.getBlockEntity(blockPos3);
                            if (blockEntity != null) {
                                if (blockEntity2 == null) {
                                    return OptionalInt.empty();
                                }

                                if (blockEntity2.getType() != blockEntity.getType()) {
                                    return OptionalInt.empty();
                                }

                                if (!blockEntity.components().equals(blockEntity2.components())) {
                                    return OptionalInt.empty();
                                }

                                CompoundTag compoundTag = blockEntity.saveCustomOnly(registryAccess);
                                CompoundTag compoundTag2 = blockEntity2.saveCustomOnly(registryAccess);
                                if (!compoundTag.equals(compoundTag2)) {
                                    return OptionalInt.empty();
                                }
                            }

                            j++;
                        }
                    }
                }
            }

            return OptionalInt.of(j);
        }
    }

    private static RedirectModifier<CommandSourceStack> expandOneToOneEntityRelation(Function<Entity, Optional<Entity>> function) {
        return context -> {
            CommandSourceStack commandSourceStack = context.getSource();
            Entity entity = commandSourceStack.getEntity();
            return entity == null
                ? List.of()
                : function.apply(entity)
                    .filter(entityx -> !entityx.isRemoved())
                    .map(entityx -> List.of(commandSourceStack.withEntity(entityx)))
                    .orElse(List.of());
        };
    }

    private static RedirectModifier<CommandSourceStack> expandOneToManyEntityRelation(Function<Entity, Stream<Entity>> function) {
        return context -> {
            CommandSourceStack commandSourceStack = context.getSource();
            Entity entity = commandSourceStack.getEntity();
            return entity == null ? List.of() : function.apply(entity).filter(entityx -> !entityx.isRemoved()).map(commandSourceStack::withEntity).toList();
        };
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRelationOperations(
        CommandNode<CommandSourceStack> node, LiteralArgumentBuilder<CommandSourceStack> builder
    ) {
        return builder.then(
                Commands.literal("owner")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            entity -> entity instanceof OwnableEntity ownableEntity ? Optional.ofNullable(ownableEntity.getOwner()) : Optional.empty()
                        )
                    )
            )
            .then(
                Commands.literal("leasher")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            entity -> entity instanceof Leashable leashable ? Optional.ofNullable(leashable.getLeashHolder()) : Optional.empty()
                        )
                    )
            )
            .then(
                Commands.literal("target")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            entity -> entity instanceof Targeting targeting ? Optional.ofNullable(targeting.getTarget()) : Optional.empty()
                        )
                    )
            )
            .then(
                Commands.literal("attacker")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            entity -> entity instanceof Attackable attackable ? Optional.ofNullable(attackable.getLastAttacker()) : Optional.empty()
                        )
                    )
            )
            .then(Commands.literal("vehicle").fork(node, expandOneToOneEntityRelation(entity -> Optional.ofNullable(entity.getVehicle()))))
            .then(Commands.literal("controller").fork(node, expandOneToOneEntityRelation(entity -> Optional.ofNullable(entity.getControllingPassenger()))))
            .then(
                Commands.literal("origin")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            entity -> entity instanceof TraceableEntity traceableEntity ? Optional.ofNullable(traceableEntity.getOwner()) : Optional.empty()
                        )
                    )
            )
            .then(Commands.literal("passengers").fork(node, expandOneToManyEntityRelation(entity -> entity.getPassengers().stream())));
    }

    private static CommandSourceStack spawnEntityAndRedirect(CommandSourceStack source, Holder.Reference<EntityType<?>> entityType) throws CommandSyntaxException {
        Entity entity = SummonCommand.createEntity(source, entityType, source.getPosition(), new CompoundTag(), true);
        return source.withEntity(entity);
    }

    public static <T extends ExecutionCommandSource<T>> void scheduleFunctionConditionsAndTest(
        T baseSource,
        List<T> sources,
        Function<T, T> functionSourceGetter,
        IntPredicate predicate,
        ContextChain<T> contextChain,
        @Nullable CompoundTag args,
        ExecutionControl<T> control,
        ExecuteCommand.CommandGetter<T, Collection<CommandFunction<T>>> functionNamesGetter,
        ChainModifiers flags
    ) {
        List<T> list = new ArrayList<>(sources.size());

        Collection<CommandFunction<T>> collection;
        try {
            collection = functionNamesGetter.get(contextChain.getTopContext().copyFor(baseSource));
        } catch (CommandSyntaxException var18) {
            baseSource.handleError(var18, flags.isForked(), control.tracer());
            return;
        }

        int i = collection.size();
        if (i != 0) {
            List<InstantiatedFunction<T>> list2 = new ArrayList<>(i);

            try {
                for (CommandFunction<T> commandFunction : collection) {
                    try {
                        list2.add(commandFunction.instantiate(args, baseSource.dispatcher()));
                    } catch (FunctionInstantiationException var17) {
                        throw ERROR_FUNCTION_CONDITION_INSTANTATION_FAILURE.create(commandFunction.id(), var17.messageComponent());
                    }
                }
            } catch (CommandSyntaxException var19) {
                baseSource.handleError(var19, flags.isForked(), control.tracer());
            }

            for (T executionCommandSource : sources) {
                T executionCommandSource2 = (T)functionSourceGetter.apply(executionCommandSource.clearCallbacks());
                CommandResultCallback commandResultCallback = (successful, returnValue) -> {
                    if (predicate.test(returnValue)) {
                        list.add(executionCommandSource);
                    }
                };
                control.queueNext(
                    new IsolatedCall<>(
                        newControl -> {
                            for (InstantiatedFunction<T> instantiatedFunction : list2) {
                                newControl.queueNext(
                                    new CallFunction<>(instantiatedFunction, newControl.currentFrame().returnValueConsumer(), true)
                                        .bind(executionCommandSource2)
                                );
                            }

                            newControl.queueNext(FallthroughTask.instance());
                        },
                        commandResultCallback
                    )
                );
            }

            ContextChain<T> contextChain2 = contextChain.nextStage();
            String string = contextChain.getTopContext().getInput();
            control.queueNext(new BuildContexts.Continuation<>(string, contextChain2, flags, baseSource, list));
        }
    }

    @FunctionalInterface
    public interface CommandGetter<T, R> {
        R get(CommandContext<T> context) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface CommandNumericPredicate {
        int test(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface CommandPredicate {
        boolean test(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    static class ExecuteIfFunctionCustomModifier implements CustomModifierExecutor.ModifierAdapter<CommandSourceStack> {
        private final IntPredicate check;

        ExecuteIfFunctionCustomModifier(boolean success) {
            this.check = success ? result -> result != 0 : result -> result == 0;
        }

        @Override
        public void apply(
            CommandSourceStack baseSource,
            List<CommandSourceStack> sources,
            ContextChain<CommandSourceStack> contextChain,
            ChainModifiers flags,
            ExecutionControl<CommandSourceStack> control
        ) {
            ExecuteCommand.scheduleFunctionConditionsAndTest(
                baseSource,
                sources,
                FunctionCommand::modifySenderForExecution,
                this.check,
                contextChain,
                null,
                control,
                context -> FunctionArgument.getFunctions(context, "name"),
                flags
            );
        }
    }

    @FunctionalInterface
    interface IntBiPredicate {
        boolean test(int targetScore, int sourceScore);
    }
}
