package net.minecraft.gametest.framework;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

public class GameTestAssertPosException extends GameTestAssertException {
    private final BlockPos absolutePos;
    private final BlockPos relativePos;
    private final long tick;

    public GameTestAssertPosException(String message, BlockPos pos, BlockPos relativePos, long tick) {
        super(message);
        this.absolutePos = pos;
        this.relativePos = relativePos;
        this.tick = tick;
    }

    @Override
    public String getMessage() {
        String string = this.absolutePos.getX()
            + ","
            + this.absolutePos.getY()
            + ","
            + this.absolutePos.getZ()
            + " (relative: "
            + this.relativePos.getX()
            + ","
            + this.relativePos.getY()
            + ","
            + this.relativePos.getZ()
            + ")";
        return super.getMessage() + " at " + string + " (t=" + this.tick + ")";
    }

    @Nullable
    public String getMessageToShowAtBlock() {
        return super.getMessage();
    }

    @Nullable
    public BlockPos getRelativePos() {
        return this.relativePos;
    }

    @Nullable
    public BlockPos getAbsolutePos() {
        return this.absolutePos;
    }
}
