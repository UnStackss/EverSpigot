package net.minecraft.gametest.framework;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import org.slf4j.Logger;

public class LogTestReporter implements TestReporter {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onTestFailed(GameTestInfo test) {
        String string = test.getStructureBlockPos().toShortString();
        if (test.isRequired()) {
            LOGGER.error("{} failed at {}! {}", test.getTestName(), string, Util.describeError(test.getError()));
        } else {
            LOGGER.warn("(optional) {} failed at {}. {}", test.getTestName(), string, Util.describeError(test.getError()));
        }
    }

    @Override
    public void onTestSuccess(GameTestInfo test) {
    }
}
