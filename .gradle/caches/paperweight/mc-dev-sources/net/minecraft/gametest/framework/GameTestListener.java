package net.minecraft.gametest.framework;

public interface GameTestListener {
    void testStructureLoaded(GameTestInfo test);

    void testPassed(GameTestInfo test, GameTestRunner context);

    void testFailed(GameTestInfo test, GameTestRunner context);

    void testAddedForRerun(GameTestInfo prevState, GameTestInfo nextState, GameTestRunner context);
}
