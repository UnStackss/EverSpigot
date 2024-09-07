package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class CloneCommands {
    private static final SimpleCommandExceptionType ERROR_OVERLAP = new SimpleCommandExceptionType(Component.translatable("commands.clone.overlap"));
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType(
        (maxCount, count) -> Component.translatableEscape("commands.clone.toobig", maxCount, count)
    );
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.clone.failed"));
    public static final Predicate<BlockInWorld> FILTER_AIR = pos -> !pos.getState().isAir();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandRegistryAccess) {
        dispatcher.register(
            Commands.literal("clone")
                .requires(source -> source.hasPermission(2))
                .then(beginEndDestinationAndModeSuffix(commandRegistryAccess, context -> context.getSource().getLevel()))
                .then(
                    Commands.literal("from")
                        .then(
                            Commands.argument("sourceDimension", DimensionArgument.dimension())
                                .then(
                                    beginEndDestinationAndModeSuffix(
                                        commandRegistryAccess, context -> DimensionArgument.getDimension(context, "sourceDimension")
                                    )
                                )
                        )
                )
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> beginEndDestinationAndModeSuffix(
        CommandBuildContext commandRegistryAccess, CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, ServerLevel> worldGetter
    ) {
        return Commands.argument("begin", BlockPosArgument.blockPos())
            .then(
                Commands.argument("end", BlockPosArgument.blockPos())
                    .then(destinationAndModeSuffix(commandRegistryAccess, worldGetter, context -> context.getSource().getLevel()))
                    .then(
                        Commands.literal("to")
                            .then(
                                Commands.argument("targetDimension", DimensionArgument.dimension())
                                    .then(
                                        destinationAndModeSuffix(
                                            commandRegistryAccess, worldGetter, context -> DimensionArgument.getDimension(context, "targetDimension")
                                        )
                                    )
                            )
                    )
            );
    }

    private static CloneCommands.DimensionAndPosition getLoadedDimensionAndPosition(CommandContext<CommandSourceStack> context, ServerLevel world, String name) throws CommandSyntaxException {
        BlockPos blockPos = BlockPosArgument.getLoadedBlockPos(context, world, name);
        return new CloneCommands.DimensionAndPosition(world, blockPos);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> destinationAndModeSuffix(
        CommandBuildContext commandRegistryAccess,
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, ServerLevel> sourceWorldGetter,
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, ServerLevel> targetWorldGetter
    ) {
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> commandFunction = context -> getLoadedDimensionAndPosition(
                context, sourceWorldGetter.apply(context), "begin"
            );
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> commandFunction2 = context -> getLoadedDimensionAndPosition(
                context, sourceWorldGetter.apply(context), "end"
            );
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> commandFunction3 = context -> getLoadedDimensionAndPosition(
                context, targetWorldGetter.apply(context), "destination"
            );
        return Commands.argument("destination", BlockPosArgument.blockPos())
            .executes(
                context -> clone(
                        context.getSource(),
                        commandFunction.apply(context),
                        commandFunction2.apply(context),
                        commandFunction3.apply(context),
                        pos -> true,
                        CloneCommands.Mode.NORMAL
                    )
            )
            .then(
                wrapWithCloneMode(
                    commandFunction,
                    commandFunction2,
                    commandFunction3,
                    context -> blockInWorld -> true,
                    Commands.literal("replace")
                        .executes(
                            context -> clone(
                                    context.getSource(),
                                    commandFunction.apply(context),
                                    commandFunction2.apply(context),
                                    commandFunction3.apply(context),
                                    pos -> true,
                                    CloneCommands.Mode.NORMAL
                                )
                        )
                )
            )
            .then(
                wrapWithCloneMode(
                    commandFunction,
                    commandFunction2,
                    commandFunction3,
                    context -> FILTER_AIR,
                    Commands.literal("masked")
                        .executes(
                            context -> clone(
                                    context.getSource(),
                                    commandFunction.apply(context),
                                    commandFunction2.apply(context),
                                    commandFunction3.apply(context),
                                    FILTER_AIR,
                                    CloneCommands.Mode.NORMAL
                                )
                        )
                )
            )
            .then(
                Commands.literal("filtered")
                    .then(
                        wrapWithCloneMode(
                            commandFunction,
                            commandFunction2,
                            commandFunction3,
                            context -> BlockPredicateArgument.getBlockPredicate(context, "filter"),
                            Commands.argument("filter", BlockPredicateArgument.blockPredicate(commandRegistryAccess))
                                .executes(
                                    context -> clone(
                                            context.getSource(),
                                            commandFunction.apply(context),
                                            commandFunction2.apply(context),
                                            commandFunction3.apply(context),
                                            BlockPredicateArgument.getBlockPredicate(context, "filter"),
                                            CloneCommands.Mode.NORMAL
                                        )
                                )
                        )
                    )
            );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapWithCloneMode(
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> beginPosGetter,
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> endPosGetter,
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> destinationPosGetter,
        CloneCommands.CommandFunction<CommandContext<CommandSourceStack>, Predicate<BlockInWorld>> filterGetter,
        ArgumentBuilder<CommandSourceStack, ?> builder
    ) {
        return builder.then(
                Commands.literal("force")
                    .executes(
                        context -> clone(
                                context.getSource(),
                                beginPosGetter.apply(context),
                                endPosGetter.apply(context),
                                destinationPosGetter.apply(context),
                                filterGetter.apply(context),
                                CloneCommands.Mode.FORCE
                            )
                    )
            )
            .then(
                Commands.literal("move")
                    .executes(
                        context -> clone(
                                context.getSource(),
                                beginPosGetter.apply(context),
                                endPosGetter.apply(context),
                                destinationPosGetter.apply(context),
                                filterGetter.apply(context),
                                CloneCommands.Mode.MOVE
                            )
                    )
            )
            .then(
                Commands.literal("normal")
                    .executes(
                        context -> clone(
                                context.getSource(),
                                beginPosGetter.apply(context),
                                endPosGetter.apply(context),
                                destinationPosGetter.apply(context),
                                filterGetter.apply(context),
                                CloneCommands.Mode.NORMAL
                            )
                    )
            );
    }

    private static int clone(
        CommandSourceStack source,
        CloneCommands.DimensionAndPosition begin,
        CloneCommands.DimensionAndPosition end,
        CloneCommands.DimensionAndPosition destination,
        Predicate<BlockInWorld> filter,
        CloneCommands.Mode mode
    ) throws CommandSyntaxException {
        BlockPos blockPos = begin.position();
        BlockPos blockPos2 = end.position();
        BoundingBox boundingBox = BoundingBox.fromCorners(blockPos, blockPos2);
        BlockPos blockPos3 = destination.position();
        BlockPos blockPos4 = blockPos3.offset(boundingBox.getLength());
        BoundingBox boundingBox2 = BoundingBox.fromCorners(blockPos3, blockPos4);
        ServerLevel serverLevel = begin.dimension();
        ServerLevel serverLevel2 = destination.dimension();
        if (!mode.canOverlap() && serverLevel == serverLevel2 && boundingBox2.intersects(boundingBox)) {
            throw ERROR_OVERLAP.create();
        } else {
            int i = boundingBox.getXSpan() * boundingBox.getYSpan() * boundingBox.getZSpan();
            int j = source.getLevel().getGameRules().getInt(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT);
            if (i > j) {
                throw ERROR_AREA_TOO_LARGE.create(j, i);
            } else if (serverLevel.hasChunksAt(blockPos, blockPos2) && serverLevel2.hasChunksAt(blockPos3, blockPos4)) {
                List<CloneCommands.CloneBlockInfo> list = Lists.newArrayList();
                List<CloneCommands.CloneBlockInfo> list2 = Lists.newArrayList();
                List<CloneCommands.CloneBlockInfo> list3 = Lists.newArrayList();
                Deque<BlockPos> deque = Lists.newLinkedList();
                BlockPos blockPos5 = new BlockPos(
                    boundingBox2.minX() - boundingBox.minX(), boundingBox2.minY() - boundingBox.minY(), boundingBox2.minZ() - boundingBox.minZ()
                );

                for (int k = boundingBox.minZ(); k <= boundingBox.maxZ(); k++) {
                    for (int l = boundingBox.minY(); l <= boundingBox.maxY(); l++) {
                        for (int m = boundingBox.minX(); m <= boundingBox.maxX(); m++) {
                            BlockPos blockPos6 = new BlockPos(m, l, k);
                            BlockPos blockPos7 = blockPos6.offset(blockPos5);
                            BlockInWorld blockInWorld = new BlockInWorld(serverLevel, blockPos6, false);
                            BlockState blockState = blockInWorld.getState();
                            if (filter.test(blockInWorld)) {
                                BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos6);
                                if (blockEntity != null) {
                                    CloneCommands.CloneBlockEntityInfo cloneBlockEntityInfo = new CloneCommands.CloneBlockEntityInfo(
                                        blockEntity.saveCustomOnly(source.registryAccess()), blockEntity.components()
                                    );
                                    list2.add(new CloneCommands.CloneBlockInfo(blockPos7, blockState, cloneBlockEntityInfo));
                                    deque.addLast(blockPos6);
                                } else if (!blockState.isSolidRender(serverLevel, blockPos6) && !blockState.isCollisionShapeFullBlock(serverLevel, blockPos6)) {
                                    list3.add(new CloneCommands.CloneBlockInfo(blockPos7, blockState, null));
                                    deque.addFirst(blockPos6);
                                } else {
                                    list.add(new CloneCommands.CloneBlockInfo(blockPos7, blockState, null));
                                    deque.addLast(blockPos6);
                                }
                            }
                        }
                    }
                }

                if (mode == CloneCommands.Mode.MOVE) {
                    for (BlockPos blockPos8 : deque) {
                        BlockEntity blockEntity2 = serverLevel.getBlockEntity(blockPos8);
                        Clearable.tryClear(blockEntity2);
                        serverLevel.setBlock(blockPos8, Blocks.BARRIER.defaultBlockState(), 2);
                    }

                    for (BlockPos blockPos9 : deque) {
                        serverLevel.setBlock(blockPos9, Blocks.AIR.defaultBlockState(), 3);
                    }
                }

                List<CloneCommands.CloneBlockInfo> list4 = Lists.newArrayList();
                list4.addAll(list);
                list4.addAll(list2);
                list4.addAll(list3);
                List<CloneCommands.CloneBlockInfo> list5 = Lists.reverse(list4);

                for (CloneCommands.CloneBlockInfo cloneBlockInfo : list5) {
                    BlockEntity blockEntity3 = serverLevel2.getBlockEntity(cloneBlockInfo.pos);
                    Clearable.tryClear(blockEntity3);
                    serverLevel2.setBlock(cloneBlockInfo.pos, Blocks.BARRIER.defaultBlockState(), 2);
                }

                int n = 0;

                for (CloneCommands.CloneBlockInfo cloneBlockInfo2 : list4) {
                    if (serverLevel2.setBlock(cloneBlockInfo2.pos, cloneBlockInfo2.state, 2)) {
                        n++;
                    }
                }

                for (CloneCommands.CloneBlockInfo cloneBlockInfo3 : list2) {
                    BlockEntity blockEntity4 = serverLevel2.getBlockEntity(cloneBlockInfo3.pos);
                    if (cloneBlockInfo3.blockEntityInfo != null && blockEntity4 != null) {
                        blockEntity4.loadCustomOnly(cloneBlockInfo3.blockEntityInfo.tag, serverLevel2.registryAccess());
                        blockEntity4.setComponents(cloneBlockInfo3.blockEntityInfo.components);
                        blockEntity4.setChanged();
                    }

                    serverLevel2.setBlock(cloneBlockInfo3.pos, cloneBlockInfo3.state, 2);
                }

                for (CloneCommands.CloneBlockInfo cloneBlockInfo4 : list5) {
                    serverLevel2.blockUpdated(cloneBlockInfo4.pos, cloneBlockInfo4.state.getBlock());
                }

                serverLevel2.getBlockTicks().copyAreaFrom(serverLevel.getBlockTicks(), boundingBox, blockPos5);
                if (n == 0) {
                    throw ERROR_FAILED.create();
                } else {
                    int o = n;
                    source.sendSuccess(() -> Component.translatable("commands.clone.success", o), true);
                    return n;
                }
            } else {
                throw BlockPosArgument.ERROR_NOT_LOADED.create();
            }
        }
    }

    static record CloneBlockEntityInfo(CompoundTag tag, DataComponentMap components) {
    }

    static record CloneBlockInfo(BlockPos pos, BlockState state, @Nullable CloneCommands.CloneBlockEntityInfo blockEntityInfo) {
    }

    @FunctionalInterface
    interface CommandFunction<T, R> {
        R apply(T value) throws CommandSyntaxException;
    }

    static record DimensionAndPosition(ServerLevel dimension, BlockPos position) {
    }

    static enum Mode {
        FORCE(true),
        MOVE(true),
        NORMAL(false);

        private final boolean canOverlap;

        private Mode(final boolean allowsOverlap) {
            this.canOverlap = allowsOverlap;
        }

        public boolean canOverlap() {
            return this.canOverlap;
        }
    }
}
