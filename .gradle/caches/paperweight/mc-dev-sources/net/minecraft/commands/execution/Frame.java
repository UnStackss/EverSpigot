package net.minecraft.commands.execution;

import net.minecraft.commands.CommandResultCallback;

public record Frame(int depth, CommandResultCallback returnValueConsumer, Frame.FrameControl frameControl) {
    public void returnSuccess(int returnValue) {
        this.returnValueConsumer.onSuccess(returnValue);
    }

    public void returnFailure() {
        this.returnValueConsumer.onFailure();
    }

    public void discard() {
        this.frameControl.discard();
    }

    @FunctionalInterface
    public interface FrameControl {
        void discard();
    }
}
