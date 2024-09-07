package net.minecraft.commands.functions;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record PlainTextFunction<T>(@Override ResourceLocation id, @Override List<UnboundEntryAction<T>> entries)
    implements CommandFunction<T>,
    InstantiatedFunction<T> {
    @Override
    public InstantiatedFunction<T> instantiate(@Nullable CompoundTag arguments, CommandDispatcher<T> dispatcher) throws FunctionInstantiationException {
        return this;
    }
}
