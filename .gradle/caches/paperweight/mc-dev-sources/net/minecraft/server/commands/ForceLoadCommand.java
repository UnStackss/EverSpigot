package net.minecraft.server.commands;

import com.google.common.base.Joiner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class ForceLoadCommand {
    private static final int MAX_CHUNK_LIMIT = 256;
    private static final Dynamic2CommandExceptionType ERROR_TOO_MANY_CHUNKS = new Dynamic2CommandExceptionType(
        (maxCount, count) -> Component.translatableEscape("commands.forceload.toobig", maxCount, count)
    );
    private static final Dynamic2CommandExceptionType ERROR_NOT_TICKING = new Dynamic2CommandExceptionType(
        (chunkPos, registryKey) -> Component.translatableEscape("commands.forceload.query.failure", chunkPos, registryKey)
    );
    private static final SimpleCommandExceptionType ERROR_ALL_ADDED = new SimpleCommandExceptionType(Component.translatable("commands.forceload.added.failure"));
    private static final SimpleCommandExceptionType ERROR_NONE_REMOVED = new SimpleCommandExceptionType(
        Component.translatable("commands.forceload.removed.failure")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("forceload")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("from", ColumnPosArgument.columnPos())
                                .executes(
                                    context -> changeForceLoad(
                                            context.getSource(),
                                            ColumnPosArgument.getColumnPos(context, "from"),
                                            ColumnPosArgument.getColumnPos(context, "from"),
                                            true
                                        )
                                )
                                .then(
                                    Commands.argument("to", ColumnPosArgument.columnPos())
                                        .executes(
                                            context -> changeForceLoad(
                                                    context.getSource(),
                                                    ColumnPosArgument.getColumnPos(context, "from"),
                                                    ColumnPosArgument.getColumnPos(context, "to"),
                                                    true
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument("from", ColumnPosArgument.columnPos())
                                .executes(
                                    context -> changeForceLoad(
                                            context.getSource(),
                                            ColumnPosArgument.getColumnPos(context, "from"),
                                            ColumnPosArgument.getColumnPos(context, "from"),
                                            false
                                        )
                                )
                                .then(
                                    Commands.argument("to", ColumnPosArgument.columnPos())
                                        .executes(
                                            context -> changeForceLoad(
                                                    context.getSource(),
                                                    ColumnPosArgument.getColumnPos(context, "from"),
                                                    ColumnPosArgument.getColumnPos(context, "to"),
                                                    false
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("all").executes(context -> removeAll(context.getSource())))
                )
                .then(
                    Commands.literal("query")
                        .executes(context -> listForceLoad(context.getSource()))
                        .then(
                            Commands.argument("pos", ColumnPosArgument.columnPos())
                                .executes(context -> queryForceLoad(context.getSource(), ColumnPosArgument.getColumnPos(context, "pos")))
                        )
                )
        );
    }

    private static int queryForceLoad(CommandSourceStack source, ColumnPos pos) throws CommandSyntaxException {
        ChunkPos chunkPos = pos.toChunkPos();
        ServerLevel serverLevel = source.getLevel();
        ResourceKey<Level> resourceKey = serverLevel.dimension();
        boolean bl = serverLevel.getForcedChunks().contains(chunkPos.toLong());
        if (bl) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.forceload.query.success", Component.translationArg(chunkPos), Component.translationArg(resourceKey.location())
                    ),
                false
            );
            return 1;
        } else {
            throw ERROR_NOT_TICKING.create(chunkPos, resourceKey.location());
        }
    }

    private static int listForceLoad(CommandSourceStack source) {
        ServerLevel serverLevel = source.getLevel();
        ResourceKey<Level> resourceKey = serverLevel.dimension();
        LongSet longSet = serverLevel.getForcedChunks();
        int i = longSet.size();
        if (i > 0) {
            String string = Joiner.on(", ").join(longSet.stream().sorted().map(ChunkPos::new).map(ChunkPos::toString).iterator());
            if (i == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.forceload.list.single", Component.translationArg(resourceKey.location()), string), false
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable("commands.forceload.list.multiple", i, Component.translationArg(resourceKey.location()), string), false
                );
            }
        } else {
            source.sendFailure(Component.translatable("commands.forceload.added.none", Component.translationArg(resourceKey.location())));
        }

        return i;
    }

    private static int removeAll(CommandSourceStack source) {
        ServerLevel serverLevel = source.getLevel();
        ResourceKey<Level> resourceKey = serverLevel.dimension();
        LongSet longSet = serverLevel.getForcedChunks();
        longSet.forEach(chunkPos -> serverLevel.setChunkForced(ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos), false));
        source.sendSuccess(() -> Component.translatable("commands.forceload.removed.all", Component.translationArg(resourceKey.location())), true);
        return 0;
    }

    private static int changeForceLoad(CommandSourceStack source, ColumnPos from, ColumnPos to, boolean forceLoaded) throws CommandSyntaxException {
        int i = Math.min(from.x(), to.x());
        int j = Math.min(from.z(), to.z());
        int k = Math.max(from.x(), to.x());
        int l = Math.max(from.z(), to.z());
        if (i >= -30000000 && j >= -30000000 && k < 30000000 && l < 30000000) {
            int m = SectionPos.blockToSectionCoord(i);
            int n = SectionPos.blockToSectionCoord(j);
            int o = SectionPos.blockToSectionCoord(k);
            int p = SectionPos.blockToSectionCoord(l);
            long q = ((long)(o - m) + 1L) * ((long)(p - n) + 1L);
            if (q > 256L) {
                throw ERROR_TOO_MANY_CHUNKS.create(256, q);
            } else {
                ServerLevel serverLevel = source.getLevel();
                ResourceKey<Level> resourceKey = serverLevel.dimension();
                ChunkPos chunkPos = null;
                int r = 0;

                for (int s = m; s <= o; s++) {
                    for (int t = n; t <= p; t++) {
                        boolean bl = serverLevel.setChunkForced(s, t, forceLoaded);
                        if (bl) {
                            r++;
                            if (chunkPos == null) {
                                chunkPos = new ChunkPos(s, t);
                            }
                        }
                    }
                }

                ChunkPos chunkPos2 = chunkPos;
                int u = r;
                if (u == 0) {
                    throw (forceLoaded ? ERROR_ALL_ADDED : ERROR_NONE_REMOVED).create();
                } else {
                    if (u == 1) {
                        source.sendSuccess(
                            () -> Component.translatable(
                                    "commands.forceload." + (forceLoaded ? "added" : "removed") + ".single",
                                    Component.translationArg(chunkPos2),
                                    Component.translationArg(resourceKey.location())
                                ),
                            true
                        );
                    } else {
                        ChunkPos chunkPos3 = new ChunkPos(m, n);
                        ChunkPos chunkPos4 = new ChunkPos(o, p);
                        source.sendSuccess(
                            () -> Component.translatable(
                                    "commands.forceload." + (forceLoaded ? "added" : "removed") + ".multiple",
                                    u,
                                    Component.translationArg(resourceKey.location()),
                                    Component.translationArg(chunkPos3),
                                    Component.translationArg(chunkPos4)
                                ),
                            true
                        );
                    }

                    return u;
                }
            }
        } else {
            throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
        }
    }
}
