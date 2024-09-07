package net.minecraft.gametest.framework;

import com.google.common.base.MoreObjects;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.exception.ExceptionUtils;

class ReportGameListener implements GameTestListener {
    private int attempts = 0;
    private int successes = 0;

    public ReportGameListener() {
    }

    @Override
    public void testStructureLoaded(GameTestInfo test) {
        spawnBeacon(test, Blocks.LIGHT_GRAY_STAINED_GLASS);
        this.attempts++;
    }

    private void handleRetry(GameTestInfo state, GameTestRunner context, boolean prevPassed) {
        RetryOptions retryOptions = state.retryOptions();
        String string = String.format("[Run: %4d, Ok: %4d, Fail: %4d", this.attempts, this.successes, this.attempts - this.successes);
        if (!retryOptions.unlimitedTries()) {
            string = string + String.format(", Left: %4d", retryOptions.numberOfTries() - this.attempts);
        }

        string = string + "]";
        String string2 = state.getTestName() + " " + (prevPassed ? "passed" : "failed") + "! " + state.getRunTime() + "ms";
        String string3 = String.format("%-53s%s", string, string2);
        if (prevPassed) {
            reportPassed(state, string3);
        } else {
            say(state.getLevel(), ChatFormatting.RED, string3);
        }

        if (retryOptions.hasTriesLeft(this.attempts, this.successes)) {
            context.rerunTest(state);
        }
    }

    @Override
    public void testPassed(GameTestInfo test, GameTestRunner context) {
        this.successes++;
        if (test.retryOptions().hasRetries()) {
            this.handleRetry(test, context, true);
        } else if (!test.isFlaky()) {
            reportPassed(test, test.getTestName() + " passed! (" + test.getRunTime() + "ms)");
        } else {
            if (this.successes >= test.requiredSuccesses()) {
                reportPassed(test, test + " passed " + this.successes + " times of " + this.attempts + " attempts.");
            } else {
                say(test.getLevel(), ChatFormatting.GREEN, "Flaky test " + test + " succeeded, attempt: " + this.attempts + " successes: " + this.successes);
                context.rerunTest(test);
            }
        }
    }

    @Override
    public void testFailed(GameTestInfo test, GameTestRunner context) {
        if (!test.isFlaky()) {
            reportFailure(test, test.getError());
            if (test.retryOptions().hasRetries()) {
                this.handleRetry(test, context, false);
            }
        } else {
            TestFunction testFunction = test.getTestFunction();
            String string = "Flaky test " + test + " failed, attempt: " + this.attempts + "/" + testFunction.maxAttempts();
            if (testFunction.requiredSuccesses() > 1) {
                string = string + ", successes: " + this.successes + " (" + testFunction.requiredSuccesses() + " required)";
            }

            say(test.getLevel(), ChatFormatting.YELLOW, string);
            if (test.maxAttempts() - this.attempts + this.successes >= test.requiredSuccesses()) {
                context.rerunTest(test);
            } else {
                reportFailure(test, new ExhaustedAttemptsException(this.attempts, this.successes, test));
            }
        }
    }

    @Override
    public void testAddedForRerun(GameTestInfo prevState, GameTestInfo nextState, GameTestRunner context) {
        nextState.addListener(this);
    }

    public static void reportPassed(GameTestInfo test, String output) {
        updateBeaconGlass(test, Blocks.LIME_STAINED_GLASS);
        visualizePassedTest(test, output);
    }

    private static void visualizePassedTest(GameTestInfo test, String output) {
        say(test.getLevel(), ChatFormatting.GREEN, output);
        GlobalTestReporter.onTestSuccess(test);
    }

    protected static void reportFailure(GameTestInfo test, Throwable output) {
        updateBeaconGlass(test, test.isRequired() ? Blocks.RED_STAINED_GLASS : Blocks.ORANGE_STAINED_GLASS);
        spawnLectern(test, Util.describeError(output));
        visualizeFailedTest(test, output);
    }

    protected static void visualizeFailedTest(GameTestInfo test, Throwable output) {
        String string = output.getMessage() + (output.getCause() == null ? "" : " cause: " + Util.describeError(output.getCause()));
        String string2 = (test.isRequired() ? "" : "(optional) ") + test.getTestName() + " failed! " + string;
        say(test.getLevel(), test.isRequired() ? ChatFormatting.RED : ChatFormatting.YELLOW, string2);
        Throwable throwable = MoreObjects.firstNonNull(ExceptionUtils.getRootCause(output), output);
        if (throwable instanceof GameTestAssertPosException gameTestAssertPosException) {
            showRedBox(test.getLevel(), gameTestAssertPosException.getAbsolutePos(), gameTestAssertPosException.getMessageToShowAtBlock());
        }

        GlobalTestReporter.onTestFailed(test);
    }

    protected static void spawnBeacon(GameTestInfo test, Block block) {
        ServerLevel serverLevel = test.getLevel();
        BlockPos blockPos = getBeaconPos(test);
        serverLevel.setBlockAndUpdate(blockPos, Blocks.BEACON.defaultBlockState().rotate(test.getRotation()));
        updateBeaconGlass(test, block);

        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                BlockPos blockPos2 = blockPos.offset(i, -1, j);
                serverLevel.setBlockAndUpdate(blockPos2, Blocks.IRON_BLOCK.defaultBlockState());
            }
        }
    }

    private static BlockPos getBeaconPos(GameTestInfo state) {
        BlockPos blockPos = state.getStructureBlockPos();
        BlockPos blockPos2 = new BlockPos(-1, -2, -1);
        return StructureTemplate.transform(blockPos.offset(blockPos2), Mirror.NONE, state.getRotation(), blockPos);
    }

    private static void updateBeaconGlass(GameTestInfo state, Block block) {
        ServerLevel serverLevel = state.getLevel();
        BlockPos blockPos = getBeaconPos(state);
        if (serverLevel.getBlockState(blockPos).is(Blocks.BEACON)) {
            BlockPos blockPos2 = blockPos.offset(0, 1, 0);
            serverLevel.setBlockAndUpdate(blockPos2, block.defaultBlockState());
        }
    }

    private static void spawnLectern(GameTestInfo test, String output) {
        ServerLevel serverLevel = test.getLevel();
        BlockPos blockPos = test.getStructureBlockPos();
        BlockPos blockPos2 = new BlockPos(-1, 0, -1);
        BlockPos blockPos3 = StructureTemplate.transform(blockPos.offset(blockPos2), Mirror.NONE, test.getRotation(), blockPos);
        serverLevel.setBlockAndUpdate(blockPos3, Blocks.LECTERN.defaultBlockState().rotate(test.getRotation()));
        BlockState blockState = serverLevel.getBlockState(blockPos3);
        ItemStack itemStack = createBook(test.getTestName(), test.isRequired(), output);
        LecternBlock.tryPlaceBook(null, serverLevel, blockPos3, blockState, itemStack);
    }

    private static ItemStack createBook(String text, boolean required, String output) {
        StringBuffer stringBuffer = new StringBuffer();
        Arrays.stream(text.split("\\.")).forEach(line -> stringBuffer.append(line).append('\n'));
        if (!required) {
            stringBuffer.append("(optional)\n");
        }

        stringBuffer.append("-------------------\n");
        ItemStack itemStack = new ItemStack(Items.WRITABLE_BOOK);
        itemStack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(Filterable.passThrough(stringBuffer + output))));
        return itemStack;
    }

    protected static void say(ServerLevel world, ChatFormatting formatting, String message) {
        world.getPlayers(player -> true).forEach(player -> player.sendSystemMessage(Component.literal(message).withStyle(formatting)));
    }

    private static void showRedBox(ServerLevel world, BlockPos pos, String message) {
        DebugPackets.sendGameTestAddMarker(world, pos, message, -2130771968, Integer.MAX_VALUE);
    }
}
