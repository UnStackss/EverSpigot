package net.minecraft.gametest.framework;

import com.mojang.brigadier.context.CommandContext;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

public class TestFinder<T> implements StructureBlockPosFinder, TestFunctionFinder {
    static final TestFunctionFinder NO_FUNCTIONS = Stream::empty;
    static final StructureBlockPosFinder NO_STRUCTURES = Stream::empty;
    private final TestFunctionFinder testFunctionFinder;
    private final StructureBlockPosFinder structureBlockPosFinder;
    private final CommandSourceStack source;
    private final Function<TestFinder<T>, T> contextProvider;

    @Override
    public Stream<BlockPos> findStructureBlockPos() {
        return this.structureBlockPosFinder.findStructureBlockPos();
    }

    TestFinder(
        CommandSourceStack commandSource,
        Function<TestFinder<T>, T> runnerFactory,
        TestFunctionFinder testFunctionFinder,
        StructureBlockPosFinder structureBlockPosFinder
    ) {
        this.source = commandSource;
        this.contextProvider = runnerFactory;
        this.testFunctionFinder = testFunctionFinder;
        this.structureBlockPosFinder = structureBlockPosFinder;
    }

    T get() {
        return this.contextProvider.apply(this);
    }

    public CommandSourceStack source() {
        return this.source;
    }

    @Override
    public Stream<TestFunction> findTestFunctions() {
        return this.testFunctionFinder.findTestFunctions();
    }

    public static class Builder<T> {
        private final Function<TestFinder<T>, T> contextProvider;
        private final UnaryOperator<Supplier<Stream<TestFunction>>> testFunctionFinderWrapper;
        private final UnaryOperator<Supplier<Stream<BlockPos>>> structureBlockPosFinderWrapper;

        public Builder(Function<TestFinder<T>, T> runnerFactory) {
            this.contextProvider = runnerFactory;
            this.testFunctionFinderWrapper = testFunctionsSupplier -> testFunctionsSupplier;
            this.structureBlockPosFinderWrapper = structurePosSupplier -> structurePosSupplier;
        }

        private Builder(
            Function<TestFinder<T>, T> runnerFactory,
            UnaryOperator<Supplier<Stream<TestFunction>>> testFunctionsSupplierMapper,
            UnaryOperator<Supplier<Stream<BlockPos>>> structurePosSupplierMapper
        ) {
            this.contextProvider = runnerFactory;
            this.testFunctionFinderWrapper = testFunctionsSupplierMapper;
            this.structureBlockPosFinderWrapper = structurePosSupplierMapper;
        }

        public TestFinder.Builder<T> createMultipleCopies(int count) {
            return new TestFinder.Builder<>(this.contextProvider, createCopies(count), createCopies(count));
        }

        private static <Q> UnaryOperator<Supplier<Stream<Q>>> createCopies(int count) {
            return supplier -> {
                List<Q> list = new LinkedList<>();
                List<Q> list2 = ((Stream)supplier.get()).toList();

                for (int j = 0; j < count; j++) {
                    list.addAll(list2);
                }

                return list::stream;
            };
        }

        private T build(CommandSourceStack source, TestFunctionFinder testFunctionFinder, StructureBlockPosFinder structureBlockFinder) {
            return new TestFinder<>(
                    source,
                    this.contextProvider,
                    this.testFunctionFinderWrapper.apply(testFunctionFinder::findTestFunctions)::get,
                    this.structureBlockPosFinderWrapper.apply(structureBlockFinder::findStructureBlockPos)::get
                )
                .get();
        }

        public T radius(CommandContext<CommandSourceStack> context, int radius) {
            CommandSourceStack commandSourceStack = context.getSource();
            BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
            return this.build(
                commandSourceStack, TestFinder.NO_FUNCTIONS, () -> StructureUtils.findStructureBlocks(blockPos, radius, commandSourceStack.getLevel())
            );
        }

        public T nearest(CommandContext<CommandSourceStack> context) {
            CommandSourceStack commandSourceStack = context.getSource();
            BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
            return this.build(
                commandSourceStack,
                TestFinder.NO_FUNCTIONS,
                () -> StructureUtils.findNearestStructureBlock(blockPos, 15, commandSourceStack.getLevel()).stream()
            );
        }

        public T allNearby(CommandContext<CommandSourceStack> context) {
            CommandSourceStack commandSourceStack = context.getSource();
            BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
            return this.build(
                commandSourceStack, TestFinder.NO_FUNCTIONS, () -> StructureUtils.findStructureBlocks(blockPos, 200, commandSourceStack.getLevel())
            );
        }

        public T lookedAt(CommandContext<CommandSourceStack> context) {
            CommandSourceStack commandSourceStack = context.getSource();
            return this.build(
                commandSourceStack,
                TestFinder.NO_FUNCTIONS,
                () -> StructureUtils.lookedAtStructureBlockPos(
                        BlockPos.containing(commandSourceStack.getPosition()), commandSourceStack.getPlayer().getCamera(), commandSourceStack.getLevel()
                    )
            );
        }

        public T allTests(CommandContext<CommandSourceStack> context) {
            return this.build(
                context.getSource(),
                () -> GameTestRegistry.getAllTestFunctions().stream().filter(testFunction -> !testFunction.manualOnly()),
                TestFinder.NO_STRUCTURES
            );
        }

        public T allTestsInClass(CommandContext<CommandSourceStack> context, String testClass) {
            return this.build(
                context.getSource(),
                () -> GameTestRegistry.getTestFunctionsForClassName(testClass).filter(testFunction -> !testFunction.manualOnly()),
                TestFinder.NO_STRUCTURES
            );
        }

        public T failedTests(CommandContext<CommandSourceStack> context, boolean onlyRequired) {
            return this.build(
                context.getSource(),
                () -> GameTestRegistry.getLastFailedTests().filter(function -> !onlyRequired || function.required()),
                TestFinder.NO_STRUCTURES
            );
        }

        public T byArgument(CommandContext<CommandSourceStack> context, String name) {
            return this.build(context.getSource(), () -> Stream.of(TestFunctionArgument.getTestFunction(context, name)), TestFinder.NO_STRUCTURES);
        }

        public T locateByName(CommandContext<CommandSourceStack> context, String name) {
            CommandSourceStack commandSourceStack = context.getSource();
            BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
            return this.build(
                commandSourceStack,
                TestFinder.NO_FUNCTIONS,
                () -> StructureUtils.findStructureByTestFunction(blockPos, 1024, commandSourceStack.getLevel(), name)
            );
        }

        public T failedTests(CommandContext<CommandSourceStack> context) {
            return this.failedTests(context, false);
        }
    }
}
