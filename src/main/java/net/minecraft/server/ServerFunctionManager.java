package net.minecraft.server;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ServerFunctionManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TICK_FUNCTION_TAG = ResourceLocation.withDefaultNamespace("tick");
    private static final ResourceLocation LOAD_FUNCTION_TAG = ResourceLocation.withDefaultNamespace("load");
    private final MinecraftServer server;
    private List<CommandFunction<CommandSourceStack>> ticking = ImmutableList.of();
    private boolean postReload;
    private ServerFunctionLibrary library;

    public ServerFunctionManager(MinecraftServer server, ServerFunctionLibrary loader) {
        this.server = server;
        this.library = loader;
        this.postReload(loader);
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return this.server.getCommands().getDispatcher(); // CraftBukkit // Paper - Don't override command dispatcher
    }

    public void tick() {
        if (this.server.tickRateManager().runsNormally()) {
            if (this.postReload) {
                this.postReload = false;
                Collection<CommandFunction<CommandSourceStack>> collection = this.library.getTag(ServerFunctionManager.LOAD_FUNCTION_TAG);

                this.executeTagFunctions(collection, ServerFunctionManager.LOAD_FUNCTION_TAG);
            }

            this.executeTagFunctions(this.ticking, ServerFunctionManager.TICK_FUNCTION_TAG);
        }
    }

    private void executeTagFunctions(Collection<CommandFunction<CommandSourceStack>> functions, ResourceLocation label) {
        ProfilerFiller gameprofilerfiller = this.server.getProfiler();

        Objects.requireNonNull(label);
        gameprofilerfiller.push(label::toString);
        Iterator iterator = functions.iterator();

        while (iterator.hasNext()) {
            CommandFunction<CommandSourceStack> commandfunction = (CommandFunction) iterator.next();

            this.execute(commandfunction, this.getGameLoopSender());
        }

        this.server.getProfiler().pop();
    }

    public void execute(CommandFunction<CommandSourceStack> function, CommandSourceStack source) {
        ProfilerFiller gameprofilerfiller = this.server.getProfiler();

        gameprofilerfiller.push(() -> {
            return "function " + String.valueOf(function.id());
        });

        try {
            InstantiatedFunction<CommandSourceStack> instantiatedfunction = function.instantiate((CompoundTag) null, this.getDispatcher());

            net.minecraft.commands.Commands.executeCommandInContext(source, (executioncontext) -> {
                ExecutionContext.queueInitialFunctionCall(executioncontext, instantiatedfunction, source, CommandResultCallback.EMPTY);
            });
        } catch (FunctionInstantiationException functioninstantiationexception) {
            ;
        } catch (Exception exception) {
            ServerFunctionManager.LOGGER.warn("Failed to execute function {}", function.id(), exception);
        } finally {
            gameprofilerfiller.pop();
        }

    }

    public void replaceLibrary(ServerFunctionLibrary loader) {
        this.library = loader;
        this.postReload(loader);
    }

    private void postReload(ServerFunctionLibrary loader) {
        this.ticking = ImmutableList.copyOf(loader.getTag(ServerFunctionManager.TICK_FUNCTION_TAG));
        this.postReload = true;
    }

    public CommandSourceStack getGameLoopSender() {
        return this.server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
    }

    public Optional<CommandFunction<CommandSourceStack>> get(ResourceLocation id) {
        return this.library.getFunction(id);
    }

    public Collection<CommandFunction<CommandSourceStack>> getTag(ResourceLocation id) {
        return this.library.getTag(id);
    }

    public Iterable<ResourceLocation> getFunctionNames() {
        return this.library.getFunctions().keySet();
    }

    public Iterable<ResourceLocation> getTagNames() {
        return this.library.getAvailableTags();
    }
}
