package net.minecraft.commands.execution;

import net.minecraft.resources.ResourceLocation;

public interface TraceCallbacks extends AutoCloseable {
    void onCommand(int depth, String command);

    void onReturn(int depth, String command, int result);

    void onError(String message);

    void onCall(int depth, ResourceLocation function, int size);

    @Override
    void close();
}
