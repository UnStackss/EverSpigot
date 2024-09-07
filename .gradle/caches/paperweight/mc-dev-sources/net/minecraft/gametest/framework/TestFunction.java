package net.minecraft.gametest.framework;

import java.util.function.Consumer;
import net.minecraft.world.level.block.Rotation;

public record TestFunction(
    String batchName,
    String testName,
    String structureName,
    Rotation rotation,
    int maxTicks,
    long setupTicks,
    boolean required,
    boolean manualOnly,
    int maxAttempts,
    int requiredSuccesses,
    boolean skyAccess,
    Consumer<GameTestHelper> function
) {
    public TestFunction(
        String batchId, String templatePath, String templateName, int tickLimit, long duration, boolean required, Consumer<GameTestHelper> starter
    ) {
        this(batchId, templatePath, templateName, Rotation.NONE, tickLimit, duration, required, false, 1, 1, false, starter);
    }

    public TestFunction(
        String batchId,
        String templatePath,
        String templateName,
        Rotation rotation,
        int tickLimit,
        long setupTicks,
        boolean required,
        Consumer<GameTestHelper> starter
    ) {
        this(batchId, templatePath, templateName, rotation, tickLimit, setupTicks, required, false, 1, 1, false, starter);
    }

    public void run(GameTestHelper context) {
        this.function.accept(context);
    }

    @Override
    public String toString() {
        return this.testName;
    }

    public boolean isFlaky() {
        return this.maxAttempts > 1;
    }
}
