package net.minecraft.server.commands;

import com.google.common.base.Stopwatch;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

public class LocateCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DynamicCommandExceptionType ERROR_STRUCTURE_NOT_FOUND = new DynamicCommandExceptionType(
        id -> Component.translatableEscape("commands.locate.structure.not_found", id)
    );
    private static final DynamicCommandExceptionType ERROR_STRUCTURE_INVALID = new DynamicCommandExceptionType(
        id -> Component.translatableEscape("commands.locate.structure.invalid", id)
    );
    private static final DynamicCommandExceptionType ERROR_BIOME_NOT_FOUND = new DynamicCommandExceptionType(
        id -> Component.translatableEscape("commands.locate.biome.not_found", id)
    );
    private static final DynamicCommandExceptionType ERROR_POI_NOT_FOUND = new DynamicCommandExceptionType(
        id -> Component.translatableEscape("commands.locate.poi.not_found", id)
    );
    private static final int MAX_STRUCTURE_SEARCH_RADIUS = 100;
    private static final int MAX_BIOME_SEARCH_RADIUS = 6400;
    private static final int BIOME_SAMPLE_RESOLUTION_HORIZONTAL = 32;
    private static final int BIOME_SAMPLE_RESOLUTION_VERTICAL = 64;
    private static final int POI_SEARCH_RADIUS = 256;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(
            Commands.literal("locate")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("structure")
                        .then(
                            Commands.argument("structure", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.STRUCTURE))
                                .executes(
                                    context -> locateStructure(
                                            context.getSource(),
                                            ResourceOrTagKeyArgument.getResourceOrTagKey(context, "structure", Registries.STRUCTURE, ERROR_STRUCTURE_INVALID)
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("biome")
                        .then(
                            Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(registryAccess, Registries.BIOME))
                                .executes(
                                    context -> locateBiome(context.getSource(), ResourceOrTagArgument.getResourceOrTag(context, "biome", Registries.BIOME))
                                )
                        )
                )
                .then(
                    Commands.literal("poi")
                        .then(
                            Commands.argument("poi", ResourceOrTagArgument.resourceOrTag(registryAccess, Registries.POINT_OF_INTEREST_TYPE))
                                .executes(
                                    context -> locatePoi(
                                            context.getSource(), ResourceOrTagArgument.getResourceOrTag(context, "poi", Registries.POINT_OF_INTEREST_TYPE)
                                        )
                                )
                        )
                )
        );
    }

    private static Optional<? extends HolderSet.ListBacked<Structure>> getHolders(
        ResourceOrTagKeyArgument.Result<Structure> predicate, Registry<Structure> structureRegistry
    ) {
        return predicate.unwrap()
            .map(key -> structureRegistry.getHolder((ResourceKey<Structure>)key).map(entry -> HolderSet.direct(entry)), structureRegistry::getTag);
    }

    private static int locateStructure(CommandSourceStack source, ResourceOrTagKeyArgument.Result<Structure> predicate) throws CommandSyntaxException {
        Registry<Structure> registry = source.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        HolderSet<Structure> holderSet = (HolderSet<Structure>)getHolders(predicate, registry)
            .orElseThrow(() -> ERROR_STRUCTURE_INVALID.create(predicate.asPrintable()));
        BlockPos blockPos = BlockPos.containing(source.getPosition());
        ServerLevel serverLevel = source.getLevel();
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Pair<BlockPos, Holder<Structure>> pair = serverLevel.getChunkSource()
            .getGenerator()
            .findNearestMapStructure(serverLevel, holderSet, blockPos, 100, false);
        stopwatch.stop();
        if (pair == null) {
            throw ERROR_STRUCTURE_NOT_FOUND.create(predicate.asPrintable());
        } else {
            return showLocateResult(source, predicate, blockPos, pair, "commands.locate.structure.success", false, stopwatch.elapsed());
        }
    }

    private static int locateBiome(CommandSourceStack source, ResourceOrTagArgument.Result<Biome> predicate) throws CommandSyntaxException {
        BlockPos blockPos = BlockPos.containing(source.getPosition());
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Pair<BlockPos, Holder<Biome>> pair = source.getLevel().findClosestBiome3d(predicate, blockPos, 6400, 32, 64);
        stopwatch.stop();
        if (pair == null) {
            throw ERROR_BIOME_NOT_FOUND.create(predicate.asPrintable());
        } else {
            return showLocateResult(source, predicate, blockPos, pair, "commands.locate.biome.success", true, stopwatch.elapsed());
        }
    }

    private static int locatePoi(CommandSourceStack source, ResourceOrTagArgument.Result<PoiType> predicate) throws CommandSyntaxException {
        BlockPos blockPos = BlockPos.containing(source.getPosition());
        ServerLevel serverLevel = source.getLevel();
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Optional<Pair<Holder<PoiType>, BlockPos>> optional = serverLevel.getPoiManager()
            .findClosestWithType(predicate, blockPos, 256, PoiManager.Occupancy.ANY);
        stopwatch.stop();
        if (optional.isEmpty()) {
            throw ERROR_POI_NOT_FOUND.create(predicate.asPrintable());
        } else {
            return showLocateResult(source, predicate, blockPos, optional.get().swap(), "commands.locate.poi.success", false, stopwatch.elapsed());
        }
    }

    public static int showLocateResult(
        CommandSourceStack source,
        ResourceOrTagArgument.Result<?> predicate,
        BlockPos currentPos,
        Pair<BlockPos, ? extends Holder<?>> result,
        String successMessage,
        boolean includeY,
        Duration timeTaken
    ) {
        String string = predicate.unwrap()
            .map(entry -> predicate.asPrintable(), tag -> predicate.asPrintable() + " (" + result.getSecond().getRegisteredName() + ")");
        return showLocateResult(source, currentPos, result, successMessage, includeY, string, timeTaken);
    }

    public static int showLocateResult(
        CommandSourceStack source,
        ResourceOrTagKeyArgument.Result<?> structure,
        BlockPos currentPos,
        Pair<BlockPos, ? extends Holder<?>> result,
        String successMessage,
        boolean includeY,
        Duration timeTaken
    ) {
        String string = structure.unwrap()
            .map(key -> key.location().toString(), key -> "#" + key.location() + " (" + result.getSecond().getRegisteredName() + ")");
        return showLocateResult(source, currentPos, result, successMessage, includeY, string, timeTaken);
    }

    private static int showLocateResult(
        CommandSourceStack source,
        BlockPos currentPos,
        Pair<BlockPos, ? extends Holder<?>> result,
        String successMessage,
        boolean includeY,
        String entryString,
        Duration timeTaken
    ) {
        BlockPos blockPos = result.getFirst();
        int i = includeY
            ? Mth.floor(Mth.sqrt((float)currentPos.distSqr(blockPos)))
            : Mth.floor(dist(currentPos.getX(), currentPos.getZ(), blockPos.getX(), blockPos.getZ()));
        String string = includeY ? String.valueOf(blockPos.getY()) : "~";
        Component component = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", blockPos.getX(), string, blockPos.getZ()))
            .withStyle(
                style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + blockPos.getX() + " " + string + " " + blockPos.getZ()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip")))
            );
        source.sendSuccess(() -> Component.translatable(successMessage, entryString, component, i), false);
        LOGGER.info("Locating element " + entryString + " took " + timeTaken.toMillis() + " ms");
        return i;
    }

    private static float dist(int x1, int y1, int x2, int y2) {
        int i = x2 - x1;
        int j = y2 - y1;
        return Mth.sqrt((float)(i * i + j * j));
    }
}
