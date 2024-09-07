package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;

public class GameTestBatchFactory {
    private static final int MAX_TESTS_PER_BATCH = 50;

    public static Collection<GameTestBatch> fromTestFunction(Collection<TestFunction> testFunctions, ServerLevel world) {
        Map<String, List<TestFunction>> map = testFunctions.stream().collect(Collectors.groupingBy(TestFunction::batchName));
        return map.entrySet()
            .stream()
            .flatMap(
                entry -> {
                    String string = entry.getKey();
                    List<TestFunction> list = entry.getValue();
                    return Streams.mapWithIndex(
                        Lists.partition(list, 50).stream(),
                        (states, index) -> toGameTestBatch(states.stream().map(testFunction -> toGameTestInfo(testFunction, 0, world)).toList(), string, index)
                    );
                }
            )
            .toList();
    }

    public static GameTestInfo toGameTestInfo(TestFunction testFunction, int rotationSteps, ServerLevel world) {
        return new GameTestInfo(testFunction, StructureUtils.getRotationForRotationSteps(rotationSteps), world, RetryOptions.noRetries());
    }

    public static GameTestRunner.GameTestBatcher fromGameTestInfo() {
        return fromGameTestInfo(50);
    }

    public static GameTestRunner.GameTestBatcher fromGameTestInfo(int batchSize) {
        return states -> {
            Map<String, List<GameTestInfo>> map = states.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(state -> state.getTestFunction().batchName()));
            return map.entrySet()
                .stream()
                .flatMap(
                    entry -> {
                        String string = entry.getKey();
                        List<GameTestInfo> list = entry.getValue();
                        return Streams.mapWithIndex(
                            Lists.partition(list, batchSize).stream(), (statesx, index) -> toGameTestBatch(List.copyOf(statesx), string, index)
                        );
                    }
                )
                .toList();
        };
    }

    public static GameTestBatch toGameTestBatch(Collection<GameTestInfo> states, String batchId, long index) {
        Consumer<ServerLevel> consumer = GameTestRegistry.getBeforeBatchFunction(batchId);
        Consumer<ServerLevel> consumer2 = GameTestRegistry.getAfterBatchFunction(batchId);
        return new GameTestBatch(batchId + ":" + index, states, consumer, consumer2);
    }
}
