package net.minecraft.gametest.framework;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.FileUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;

public class TestCommand {
    public static final int STRUCTURE_BLOCK_NEARBY_SEARCH_RADIUS = 15;
    public static final int STRUCTURE_BLOCK_FULL_SEARCH_RADIUS = 200;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_CLEAR_RADIUS = 200;
    private static final int MAX_CLEAR_RADIUS = 1024;
    private static final int TEST_POS_Z_OFFSET_FROM_PLAYER = 3;
    private static final int SHOW_POS_DURATION_MS = 10000;
    private static final int DEFAULT_X_SIZE = 5;
    private static final int DEFAULT_Y_SIZE = 5;
    private static final int DEFAULT_Z_SIZE = 5;
    private static final String STRUCTURE_BLOCK_ENTITY_COULD_NOT_BE_FOUND = "Structure block entity could not be found";
    private static final TestFinder.Builder<TestCommand.Runner> testFinder = new TestFinder.Builder<>(TestCommand.Runner::new);

    private static ArgumentBuilder<CommandSourceStack, ?> runWithRetryOptions(
        ArgumentBuilder<CommandSourceStack, ?> builder,
        Function<CommandContext<CommandSourceStack>, TestCommand.Runner> callback,
        Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> extraConfigAdder
    ) {
        return builder.executes(context -> callback.apply(context).run())
            .then(
                Commands.argument("numberOfTimes", IntegerArgumentType.integer(0))
                    .executes(context -> callback.apply(context).run(new RetryOptions(IntegerArgumentType.getInteger(context, "numberOfTimes"), false)))
                    .then(
                        extraConfigAdder.apply(
                            Commands.argument("untilFailed", BoolArgumentType.bool())
                                .executes(
                                    context -> callback.apply(context)
                                            .run(
                                                new RetryOptions(
                                                    IntegerArgumentType.getInteger(context, "numberOfTimes"), BoolArgumentType.getBool(context, "untilFailed")
                                                )
                                            )
                                )
                        )
                    )
            );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> runWithRetryOptions(
        ArgumentBuilder<CommandSourceStack, ?> builder, Function<CommandContext<CommandSourceStack>, TestCommand.Runner> callback
    ) {
        return runWithRetryOptions(builder, callback, extraConfigAdder -> extraConfigAdder);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> runWithRetryOptionsAndBuildInfo(
        ArgumentBuilder<CommandSourceStack, ?> builder, Function<CommandContext<CommandSourceStack>, TestCommand.Runner> callback
    ) {
        return runWithRetryOptions(
            builder,
            callback,
            extraConfigAdder -> extraConfigAdder.then(
                    Commands.argument("rotationSteps", IntegerArgumentType.integer())
                        .executes(
                            context -> callback.apply(context)
                                    .run(
                                        new RetryOptions(
                                            IntegerArgumentType.getInteger(context, "numberOfTimes"), BoolArgumentType.getBool(context, "untilFailed")
                                        ),
                                        IntegerArgumentType.getInteger(context, "rotationSteps")
                                    )
                        )
                        .then(
                            Commands.argument("testsPerRow", IntegerArgumentType.integer())
                                .executes(
                                    context -> callback.apply(context)
                                            .run(
                                                new RetryOptions(
                                                    IntegerArgumentType.getInteger(context, "numberOfTimes"), BoolArgumentType.getBool(context, "untilFailed")
                                                ),
                                                IntegerArgumentType.getInteger(context, "rotationSteps"),
                                                IntegerArgumentType.getInteger(context, "testsPerRow")
                                            )
                                )
                        )
                )
        );
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ArgumentBuilder<CommandSourceStack, ?> argumentBuilder = runWithRetryOptionsAndBuildInfo(
            Commands.argument("onlyRequiredTests", BoolArgumentType.bool()),
            context -> testFinder.failedTests(context, BoolArgumentType.getBool(context, "onlyRequiredTests"))
        );
        ArgumentBuilder<CommandSourceStack, ?> argumentBuilder2 = runWithRetryOptionsAndBuildInfo(
            Commands.argument("testClassName", TestClassNameArgument.testClassName()),
            context -> testFinder.allTestsInClass(context, TestClassNameArgument.getTestClassName(context, "testClassName"))
        );
        dispatcher.register(
            Commands.literal("test")
                .then(
                    Commands.literal("run")
                        .then(
                            runWithRetryOptionsAndBuildInfo(
                                Commands.argument("testName", TestFunctionArgument.testFunctionArgument()),
                                context -> testFinder.byArgument(context, "testName")
                            )
                        )
                )
                .then(
                    Commands.literal("runmultiple")
                        .then(
                            Commands.argument("testName", TestFunctionArgument.testFunctionArgument())
                                .executes(context -> testFinder.byArgument(context, "testName").run())
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(
                                            context -> testFinder.createMultipleCopies(IntegerArgumentType.getInteger(context, "amount"))
                                                    .byArgument(context, "testName")
                                                    .run()
                                        )
                                )
                        )
                )
                .then(runWithRetryOptionsAndBuildInfo(Commands.literal("runall").then(argumentBuilder2), testFinder::allTests))
                .then(runWithRetryOptions(Commands.literal("runthese"), testFinder::allNearby))
                .then(runWithRetryOptions(Commands.literal("runclosest"), testFinder::nearest))
                .then(runWithRetryOptions(Commands.literal("runthat"), testFinder::lookedAt))
                .then(runWithRetryOptionsAndBuildInfo(Commands.literal("runfailed").then(argumentBuilder), testFinder::failedTests))
                .then(
                    Commands.literal("verify")
                        .then(
                            Commands.argument("testName", TestFunctionArgument.testFunctionArgument())
                                .executes(context -> testFinder.byArgument(context, "testName").verify())
                        )
                )
                .then(
                    Commands.literal("verifyclass")
                        .then(
                            Commands.argument("testClassName", TestClassNameArgument.testClassName())
                                .executes(
                                    context -> testFinder.allTestsInClass(context, TestClassNameArgument.getTestClassName(context, "testClassName")).verify()
                                )
                        )
                )
                .then(
                    Commands.literal("locate")
                        .then(
                            Commands.argument("testName", TestFunctionArgument.testFunctionArgument())
                                .executes(
                                    context -> testFinder.locateByName(
                                                context, "minecraft:" + TestFunctionArgument.getTestFunction(context, "testName").structureName()
                                            )
                                            .locate()
                                )
                        )
                )
                .then(Commands.literal("resetclosest").executes(context -> testFinder.nearest(context).reset()))
                .then(Commands.literal("resetthese").executes(context -> testFinder.allNearby(context).reset()))
                .then(Commands.literal("resetthat").executes(context -> testFinder.lookedAt(context).reset()))
                .then(
                    Commands.literal("export")
                        .then(
                            Commands.argument("testName", StringArgumentType.word())
                                .executes(context -> exportTestStructure(context.getSource(), "minecraft:" + StringArgumentType.getString(context, "testName")))
                        )
                )
                .then(Commands.literal("exportclosest").executes(context -> testFinder.nearest(context).export()))
                .then(Commands.literal("exportthese").executes(context -> testFinder.allNearby(context).export()))
                .then(Commands.literal("exportthat").executes(context -> testFinder.lookedAt(context).export()))
                .then(Commands.literal("clearthat").executes(context -> testFinder.lookedAt(context).clear()))
                .then(Commands.literal("clearthese").executes(context -> testFinder.allNearby(context).clear()))
                .then(
                    Commands.literal("clearall")
                        .executes(context -> testFinder.radius(context, 200).clear())
                        .then(
                            Commands.argument("radius", IntegerArgumentType.integer())
                                .executes(context -> testFinder.radius(context, Mth.clamp(IntegerArgumentType.getInteger(context, "radius"), 0, 1024)).clear())
                        )
                )
                .then(
                    Commands.literal("import")
                        .then(
                            Commands.argument("testName", StringArgumentType.word())
                                .executes(context -> importTestStructure(context.getSource(), StringArgumentType.getString(context, "testName")))
                        )
                )
                .then(Commands.literal("stop").executes(context -> stopTests()))
                .then(
                    Commands.literal("pos")
                        .executes(context -> showPos(context.getSource(), "pos"))
                        .then(
                            Commands.argument("var", StringArgumentType.word())
                                .executes(context -> showPos(context.getSource(), StringArgumentType.getString(context, "var")))
                        )
                )
                .then(
                    Commands.literal("create")
                        .then(
                            Commands.argument("testName", StringArgumentType.word())
                                .suggests(TestFunctionArgument::suggestTestFunction)
                                .executes(context -> createNewStructure(context.getSource(), StringArgumentType.getString(context, "testName"), 5, 5, 5))
                                .then(
                                    Commands.argument("width", IntegerArgumentType.integer())
                                        .executes(
                                            context -> createNewStructure(
                                                    context.getSource(),
                                                    StringArgumentType.getString(context, "testName"),
                                                    IntegerArgumentType.getInteger(context, "width"),
                                                    IntegerArgumentType.getInteger(context, "width"),
                                                    IntegerArgumentType.getInteger(context, "width")
                                                )
                                        )
                                        .then(
                                            Commands.argument("height", IntegerArgumentType.integer())
                                                .then(
                                                    Commands.argument("depth", IntegerArgumentType.integer())
                                                        .executes(
                                                            context -> createNewStructure(
                                                                    context.getSource(),
                                                                    StringArgumentType.getString(context, "testName"),
                                                                    IntegerArgumentType.getInteger(context, "width"),
                                                                    IntegerArgumentType.getInteger(context, "height"),
                                                                    IntegerArgumentType.getInteger(context, "depth")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int resetGameTestInfo(GameTestInfo state) {
        state.getLevel().getEntities(null, state.getStructureBounds()).stream().forEach(entity -> entity.remove(Entity.RemovalReason.DISCARDED));
        state.getStructureBlockEntity().placeStructure(state.getLevel());
        StructureUtils.removeBarriers(state.getStructureBounds(), state.getLevel());
        say(state.getLevel(), "Reset succeded for: " + state.getTestName(), ChatFormatting.GREEN);
        return 1;
    }

    static Stream<GameTestInfo> toGameTestInfos(CommandSourceStack source, RetryOptions config, StructureBlockPosFinder finder) {
        return finder.findStructureBlockPos().map(pos -> createGameTestInfo(pos, source.getLevel(), config)).flatMap(Optional::stream);
    }

    static Stream<GameTestInfo> toGameTestInfo(CommandSourceStack source, RetryOptions config, TestFunctionFinder finder, int rotationSteps) {
        return finder.findTestFunctions()
            .filter(testFunction -> verifyStructureExists(source.getLevel(), testFunction.structureName()))
            .map(testFunction -> new GameTestInfo(testFunction, StructureUtils.getRotationForRotationSteps(rotationSteps), source.getLevel(), config));
    }

    private static Optional<GameTestInfo> createGameTestInfo(BlockPos pos, ServerLevel world, RetryOptions config) {
        StructureBlockEntity structureBlockEntity = (StructureBlockEntity)world.getBlockEntity(pos);
        if (structureBlockEntity == null) {
            say(world, "Structure block entity could not be found", ChatFormatting.RED);
            return Optional.empty();
        } else {
            String string = structureBlockEntity.getMetaData();
            Optional<TestFunction> optional = GameTestRegistry.findTestFunction(string);
            if (optional.isEmpty()) {
                say(world, "Test function for test " + string + " could not be found", ChatFormatting.RED);
                return Optional.empty();
            } else {
                TestFunction testFunction = optional.get();
                GameTestInfo gameTestInfo = new GameTestInfo(testFunction, structureBlockEntity.getRotation(), world, config);
                gameTestInfo.setStructureBlockPos(pos);
                return !verifyStructureExists(world, gameTestInfo.getStructureName()) ? Optional.empty() : Optional.of(gameTestInfo);
            }
        }
    }

    private static int createNewStructure(CommandSourceStack source, String testName, int x, int y, int z) {
        if (x <= 48 && y <= 48 && z <= 48) {
            ServerLevel serverLevel = source.getLevel();
            BlockPos blockPos = createTestPositionAround(source).below();
            StructureUtils.createNewEmptyStructureBlock(testName.toLowerCase(), blockPos, new Vec3i(x, y, z), Rotation.NONE, serverLevel);
            BlockPos blockPos2 = blockPos.above();
            BlockPos blockPos3 = blockPos2.offset(x - 1, 0, z - 1);
            BlockPos.betweenClosedStream(blockPos2, blockPos3).forEach(pos -> serverLevel.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState()));
            StructureUtils.addCommandBlockAndButtonToStartTest(blockPos, new BlockPos(1, 0, -1), Rotation.NONE, serverLevel);
            return 0;
        } else {
            throw new IllegalArgumentException("The structure must be less than 48 blocks big in each axis");
        }
    }

    private static int showPos(CommandSourceStack source, String variableName) throws CommandSyntaxException {
        BlockHitResult blockHitResult = (BlockHitResult)source.getPlayerOrException().pick(10.0, 1.0F, false);
        BlockPos blockPos = blockHitResult.getBlockPos();
        ServerLevel serverLevel = source.getLevel();
        Optional<BlockPos> optional = StructureUtils.findStructureBlockContainingPos(blockPos, 15, serverLevel);
        if (optional.isEmpty()) {
            optional = StructureUtils.findStructureBlockContainingPos(blockPos, 200, serverLevel);
        }

        if (optional.isEmpty()) {
            source.sendFailure(Component.literal("Can't find a structure block that contains the targeted pos " + blockPos));
            return 0;
        } else {
            StructureBlockEntity structureBlockEntity = (StructureBlockEntity)serverLevel.getBlockEntity(optional.get());
            if (structureBlockEntity == null) {
                say(serverLevel, "Structure block entity could not be found", ChatFormatting.RED);
                return 0;
            } else {
                BlockPos blockPos2 = blockPos.subtract(optional.get());
                String string = blockPos2.getX() + ", " + blockPos2.getY() + ", " + blockPos2.getZ();
                String string2 = structureBlockEntity.getMetaData();
                Component component = Component.literal(string)
                    .setStyle(
                        Style.EMPTY
                            .withBold(true)
                            .withColor(ChatFormatting.GREEN)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy to clipboard")))
                            .withClickEvent(
                                new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "final BlockPos " + variableName + " = new BlockPos(" + string + ");")
                            )
                    );
                source.sendSuccess(() -> Component.literal("Position relative to " + string2 + ": ").append(component), false);
                DebugPackets.sendGameTestAddMarker(serverLevel, new BlockPos(blockPos), string, -2147418368, 10000);
                return 1;
            }
        }
    }

    static int stopTests() {
        GameTestTicker.SINGLETON.clear();
        return 1;
    }

    static int trackAndStartRunner(CommandSourceStack source, ServerLevel world, GameTestRunner context) {
        context.addListener(new TestCommand.TestBatchSummaryDisplayer(source));
        MultipleTestTracker multipleTestTracker = new MultipleTestTracker(context.getTestInfos());
        multipleTestTracker.addListener(new TestCommand.TestSummaryDisplayer(world, multipleTestTracker));
        multipleTestTracker.addFailureListener(state -> GameTestRegistry.rememberFailedTest(state.getTestFunction()));
        context.start();
        return 1;
    }

    static int saveAndExportTestStructure(CommandSourceStack source, StructureBlockEntity blockEntity) {
        String string = blockEntity.getStructureName();
        if (!blockEntity.saveStructure(true)) {
            say(source, "Failed to save structure " + string);
        }

        return exportTestStructure(source, string);
    }

    private static int exportTestStructure(CommandSourceStack source, String testName) {
        Path path = Paths.get(StructureUtils.testStructuresDir);
        ResourceLocation resourceLocation = ResourceLocation.parse(testName);
        Path path2 = source.getLevel().getStructureManager().createAndValidatePathToGeneratedStructure(resourceLocation, ".nbt");
        Path path3 = NbtToSnbt.convertStructure(CachedOutput.NO_CACHE, path2, resourceLocation.getPath(), path);
        if (path3 == null) {
            say(source, "Failed to export " + path2);
            return 1;
        } else {
            try {
                FileUtil.createDirectoriesSafe(path3.getParent());
            } catch (IOException var7) {
                say(source, "Could not create folder " + path3.getParent());
                LOGGER.error("Could not create export folder", (Throwable)var7);
                return 1;
            }

            say(source, "Exported " + testName + " to " + path3.toAbsolutePath());
            return 0;
        }
    }

    private static boolean verifyStructureExists(ServerLevel world, String templateId) {
        if (world.getStructureManager().get(ResourceLocation.parse(templateId)).isEmpty()) {
            say(world, "Test structure " + templateId + " could not be found", ChatFormatting.RED);
            return false;
        } else {
            return true;
        }
    }

    static BlockPos createTestPositionAround(CommandSourceStack source) {
        BlockPos blockPos = BlockPos.containing(source.getPosition());
        int i = source.getLevel().getHeightmapPos(Heightmap.Types.WORLD_SURFACE, blockPos).getY();
        return new BlockPos(blockPos.getX(), i + 1, blockPos.getZ() + 3);
    }

    static void say(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static int importTestStructure(CommandSourceStack source, String testName) {
        Path path = Paths.get(StructureUtils.testStructuresDir, testName + ".snbt");
        ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace(testName);
        Path path2 = source.getLevel().getStructureManager().createAndValidatePathToGeneratedStructure(resourceLocation, ".nbt");

        try {
            BufferedReader bufferedReader = Files.newBufferedReader(path);
            String string = IOUtils.toString(bufferedReader);
            Files.createDirectories(path2.getParent());

            try (OutputStream outputStream = Files.newOutputStream(path2)) {
                NbtIo.writeCompressed(NbtUtils.snbtToStructure(string), outputStream);
            }

            source.getLevel().getStructureManager().remove(resourceLocation);
            say(source, "Imported to " + path2.toAbsolutePath());
            return 0;
        } catch (CommandSyntaxException | IOException var12) {
            LOGGER.error("Failed to load structure {}", testName, var12);
            return 1;
        }
    }

    static void say(ServerLevel world, String message, ChatFormatting formatting) {
        world.getPlayers(player -> true).forEach(player -> player.sendSystemMessage(Component.literal(message).withStyle(formatting)));
    }

    public static class Runner {
        private final TestFinder<TestCommand.Runner> finder;

        public Runner(TestFinder<TestCommand.Runner> finder) {
            this.finder = finder;
        }

        public int reset() {
            TestCommand.stopTests();
            return TestCommand.toGameTestInfos(this.finder.source(), RetryOptions.noRetries(), this.finder)
                    .map(TestCommand::resetGameTestInfo)
                    .toList()
                    .isEmpty()
                ? 0
                : 1;
        }

        private <T> void logAndRun(Stream<T> finder, ToIntFunction<T> consumer, Runnable emptyCallback, Consumer<Integer> finishCallback) {
            int i = finder.mapToInt(consumer).sum();
            if (i == 0) {
                emptyCallback.run();
            } else {
                finishCallback.accept(i);
            }
        }

        public int clear() {
            TestCommand.stopTests();
            CommandSourceStack commandSourceStack = this.finder.source();
            ServerLevel serverLevel = commandSourceStack.getLevel();
            GameTestRunner.clearMarkers(serverLevel);
            this.logAndRun(
                this.finder.findStructureBlockPos(),
                pos -> {
                    StructureBlockEntity structureBlockEntity = (StructureBlockEntity)serverLevel.getBlockEntity(pos);
                    if (structureBlockEntity == null) {
                        return 0;
                    } else {
                        BoundingBox boundingBox = StructureUtils.getStructureBoundingBox(structureBlockEntity);
                        StructureUtils.clearSpaceForStructure(boundingBox, serverLevel);
                        return 1;
                    }
                },
                () -> TestCommand.say(serverLevel, "Could not find any structures to clear", ChatFormatting.RED),
                count -> TestCommand.say(commandSourceStack, "Cleared " + count + " structures")
            );
            return 1;
        }

        public int export() {
            MutableBoolean mutableBoolean = new MutableBoolean(true);
            CommandSourceStack commandSourceStack = this.finder.source();
            ServerLevel serverLevel = commandSourceStack.getLevel();
            this.logAndRun(
                this.finder.findStructureBlockPos(),
                pos -> {
                    StructureBlockEntity structureBlockEntity = (StructureBlockEntity)serverLevel.getBlockEntity(pos);
                    if (structureBlockEntity == null) {
                        TestCommand.say(serverLevel, "Structure block entity could not be found", ChatFormatting.RED);
                        mutableBoolean.setFalse();
                        return 0;
                    } else {
                        if (TestCommand.saveAndExportTestStructure(commandSourceStack, structureBlockEntity) != 0) {
                            mutableBoolean.setFalse();
                        }

                        return 1;
                    }
                },
                () -> TestCommand.say(serverLevel, "Could not find any structures to export", ChatFormatting.RED),
                count -> TestCommand.say(commandSourceStack, "Exported " + count + " structures")
            );
            return mutableBoolean.getValue() ? 0 : 1;
        }

        int verify() {
            TestCommand.stopTests();
            CommandSourceStack commandSourceStack = this.finder.source();
            ServerLevel serverLevel = commandSourceStack.getLevel();
            BlockPos blockPos = TestCommand.createTestPositionAround(commandSourceStack);
            Collection<GameTestInfo> collection = Stream.concat(
                    TestCommand.toGameTestInfos(commandSourceStack, RetryOptions.noRetries(), this.finder),
                    TestCommand.toGameTestInfo(commandSourceStack, RetryOptions.noRetries(), this.finder, 0)
                )
                .toList();
            int i = 10;
            GameTestRunner.clearMarkers(serverLevel);
            GameTestRegistry.forgetFailedTests();
            Collection<GameTestBatch> collection2 = new ArrayList<>();

            for (GameTestInfo gameTestInfo : collection) {
                for (Rotation rotation : Rotation.values()) {
                    Collection<GameTestInfo> collection3 = new ArrayList<>();

                    for (int j = 0; j < 100; j++) {
                        GameTestInfo gameTestInfo2 = new GameTestInfo(gameTestInfo.getTestFunction(), rotation, serverLevel, new RetryOptions(1, true));
                        collection3.add(gameTestInfo2);
                    }

                    GameTestBatch gameTestBatch = GameTestBatchFactory.toGameTestBatch(
                        collection3, gameTestInfo.getTestFunction().batchName(), (long)rotation.ordinal()
                    );
                    collection2.add(gameTestBatch);
                }
            }

            StructureGridSpawner structureGridSpawner = new StructureGridSpawner(blockPos, 10, true);
            GameTestRunner gameTestRunner = GameTestRunner.Builder.fromBatches(collection2, serverLevel)
                .batcher(GameTestBatchFactory.fromGameTestInfo(100))
                .newStructureSpawner(structureGridSpawner)
                .existingStructureSpawner(structureGridSpawner)
                .haltOnError(true)
                .build();
            return TestCommand.trackAndStartRunner(commandSourceStack, serverLevel, gameTestRunner);
        }

        public int run(RetryOptions config, int rotationSteps, int testsPerRow) {
            TestCommand.stopTests();
            CommandSourceStack commandSourceStack = this.finder.source();
            ServerLevel serverLevel = commandSourceStack.getLevel();
            BlockPos blockPos = TestCommand.createTestPositionAround(commandSourceStack);
            Collection<GameTestInfo> collection = Stream.concat(
                    TestCommand.toGameTestInfos(commandSourceStack, config, this.finder),
                    TestCommand.toGameTestInfo(commandSourceStack, config, this.finder, rotationSteps)
                )
                .toList();
            if (collection.isEmpty()) {
                TestCommand.say(commandSourceStack, "No tests found");
                return 0;
            } else {
                GameTestRunner.clearMarkers(serverLevel);
                GameTestRegistry.forgetFailedTests();
                TestCommand.say(commandSourceStack, "Running " + collection.size() + " tests...");
                GameTestRunner gameTestRunner = GameTestRunner.Builder.fromInfo(collection, serverLevel)
                    .newStructureSpawner(new StructureGridSpawner(blockPos, testsPerRow, false))
                    .build();
                return TestCommand.trackAndStartRunner(commandSourceStack, serverLevel, gameTestRunner);
            }
        }

        public int run(int rotationSteps, int testsPerRow) {
            return this.run(RetryOptions.noRetries(), rotationSteps, testsPerRow);
        }

        public int run(int rotationSteps) {
            return this.run(RetryOptions.noRetries(), rotationSteps, 8);
        }

        public int run(RetryOptions config, int rotationSteps) {
            return this.run(config, rotationSteps, 8);
        }

        public int run(RetryOptions config) {
            return this.run(config, 0, 8);
        }

        public int run() {
            return this.run(RetryOptions.noRetries());
        }

        public int locate() {
            TestCommand.say(this.finder.source(), "Started locating test structures, this might take a while..");
            MutableInt mutableInt = new MutableInt(0);
            BlockPos blockPos = BlockPos.containing(this.finder.source().getPosition());
            this.finder
                .findStructureBlockPos()
                .forEach(
                    pos -> {
                        StructureBlockEntity structureBlockEntity = (StructureBlockEntity)this.finder.source().getLevel().getBlockEntity(pos);
                        if (structureBlockEntity != null) {
                            Direction direction = structureBlockEntity.getRotation().rotate(Direction.NORTH);
                            BlockPos blockPos2 = structureBlockEntity.getBlockPos().relative(direction, 2);
                            int ix = (int)direction.getOpposite().toYRot();
                            String string = String.format("/tp @s %d %d %d %d 0", blockPos2.getX(), blockPos2.getY(), blockPos2.getZ(), ix);
                            int j = blockPos.getX() - pos.getX();
                            int k = blockPos.getZ() - pos.getZ();
                            int l = Mth.floor(Mth.sqrt((float)(j * j + k * k)));
                            Component component = ComponentUtils.wrapInSquareBrackets(
                                    Component.translatable("chat.coordinates", pos.getX(), pos.getY(), pos.getZ())
                                )
                                .withStyle(
                                    style -> style.withColor(ChatFormatting.GREEN)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, string))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip")))
                                );
                            Component component2 = Component.literal("Found structure at: ").append(component).append(" (distance: " + l + ")");
                            this.finder.source().sendSuccess(() -> component2, false);
                            mutableInt.increment();
                        }
                    }
                );
            int i = mutableInt.intValue();
            if (i == 0) {
                TestCommand.say(this.finder.source().getLevel(), "No such test structure found", ChatFormatting.RED);
                return 0;
            } else {
                TestCommand.say(this.finder.source().getLevel(), "Finished locating, found " + i + " structure(s)", ChatFormatting.GREEN);
                return 1;
            }
        }
    }

    static record TestBatchSummaryDisplayer(CommandSourceStack source) implements GameTestBatchListener {
        @Override
        public void testBatchStarting(GameTestBatch batch) {
            TestCommand.say(this.source, "Starting batch: " + batch.name());
        }

        @Override
        public void testBatchFinished(GameTestBatch batch) {
        }
    }

    public static record TestSummaryDisplayer(ServerLevel level, MultipleTestTracker tracker) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo test) {
        }

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner context) {
            showTestSummaryIfAllDone(this.level, this.tracker);
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner context) {
            showTestSummaryIfAllDone(this.level, this.tracker);
        }

        @Override
        public void testAddedForRerun(GameTestInfo prevState, GameTestInfo nextState, GameTestRunner context) {
            this.tracker.addTestToTrack(nextState);
        }

        private static void showTestSummaryIfAllDone(ServerLevel world, MultipleTestTracker tests) {
            if (tests.isDone()) {
                TestCommand.say(world, "GameTest done! " + tests.getTotalCount() + " tests were run", ChatFormatting.WHITE);
                if (tests.hasFailedRequired()) {
                    TestCommand.say(world, tests.getFailedRequiredCount() + " required tests failed :(", ChatFormatting.RED);
                } else {
                    TestCommand.say(world, "All required tests passed :)", ChatFormatting.GREEN);
                }

                if (tests.hasFailedOptional()) {
                    TestCommand.say(world, tests.getFailedOptionalCount() + " optional tests failed", ChatFormatting.GRAY);
                }
            }
        }
    }
}
