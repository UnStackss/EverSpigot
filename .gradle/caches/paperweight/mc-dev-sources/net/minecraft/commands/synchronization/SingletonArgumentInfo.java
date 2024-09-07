package net.minecraft.commands.synchronization;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.ArgumentType;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.FriendlyByteBuf;

public class SingletonArgumentInfo<A extends ArgumentType<?>> implements ArgumentTypeInfo<A, SingletonArgumentInfo<A>.Template> {
    private final SingletonArgumentInfo<A>.Template template;

    private SingletonArgumentInfo(Function<CommandBuildContext, A> typeSupplier) {
        this.template = new SingletonArgumentInfo.Template(typeSupplier);
    }

    public static <T extends ArgumentType<?>> SingletonArgumentInfo<T> contextFree(Supplier<T> typeSupplier) {
        return new SingletonArgumentInfo<>(commandRegistryAccess -> typeSupplier.get());
    }

    public static <T extends ArgumentType<?>> SingletonArgumentInfo<T> contextAware(Function<CommandBuildContext, T> typeSupplier) {
        return new SingletonArgumentInfo<>(typeSupplier);
    }

    @Override
    public void serializeToNetwork(SingletonArgumentInfo<A>.Template properties, FriendlyByteBuf buf) {
    }

    @Override
    public void serializeToJson(SingletonArgumentInfo<A>.Template properties, JsonObject json) {
    }

    @Override
    public SingletonArgumentInfo<A>.Template deserializeFromNetwork(FriendlyByteBuf friendlyByteBuf) {
        return this.template;
    }

    @Override
    public SingletonArgumentInfo<A>.Template unpack(A argumentType) {
        return this.template;
    }

    public final class Template implements ArgumentTypeInfo.Template<A> {
        private final Function<CommandBuildContext, A> constructor;

        public Template(final Function<CommandBuildContext, A> typeSupplier) {
            this.constructor = typeSupplier;
        }

        @Override
        public A instantiate(CommandBuildContext commandRegistryAccess) {
            return this.constructor.apply(commandRegistryAccess);
        }

        @Override
        public ArgumentTypeInfo<A, ?> type() {
            return SingletonArgumentInfo.this;
        }
    }
}
