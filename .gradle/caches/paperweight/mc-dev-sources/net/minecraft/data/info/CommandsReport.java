package net.minecraft.data.info;

import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public class CommandsReport implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public CommandsReport(PackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture) {
        this.output = output;
        this.registries = registryLookupFuture;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        Path path = this.output.getOutputFolder(PackOutput.Target.REPORTS).resolve("commands.json");
        return this.registries
            .thenCompose(
                lookup -> {
                    CommandDispatcher<CommandSourceStack> commandDispatcher = new Commands(
                            Commands.CommandSelection.ALL, Commands.createValidationContext(lookup)
                        )
                        .getDispatcher();
                    return DataProvider.saveStable(writer, ArgumentUtils.serializeNodeToJson(commandDispatcher, commandDispatcher.getRoot()), path);
                }
            );
    }

    @Override
    public final String getName() {
        return "Command Syntax";
    }
}
