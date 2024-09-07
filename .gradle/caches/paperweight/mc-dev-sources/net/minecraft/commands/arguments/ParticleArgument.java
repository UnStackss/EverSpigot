package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class ParticleArgument implements ArgumentType<ParticleOptions> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "particle{foo:bar}");
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_PARTICLE = new DynamicCommandExceptionType(
        id -> Component.translatableEscape("particle.notFound", id)
    );
    public static final DynamicCommandExceptionType ERROR_INVALID_OPTIONS = new DynamicCommandExceptionType(
        error -> Component.translatableEscape("particle.invalidOptions", error)
    );
    private final HolderLookup.Provider registries;

    public ParticleArgument(CommandBuildContext registryAccess) {
        this.registries = registryAccess;
    }

    public static ParticleArgument particle(CommandBuildContext registryAccess) {
        return new ParticleArgument(registryAccess);
    }

    public static ParticleOptions getParticle(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, ParticleOptions.class);
    }

    public ParticleOptions parse(StringReader stringReader) throws CommandSyntaxException {
        return readParticle(stringReader, this.registries);
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static ParticleOptions readParticle(StringReader reader, HolderLookup.Provider registryLookup) throws CommandSyntaxException {
        ParticleType<?> particleType = readParticleType(reader, registryLookup.lookupOrThrow(Registries.PARTICLE_TYPE));
        return readParticle(reader, (ParticleType<ParticleOptions>)particleType, registryLookup);
    }

    private static ParticleType<?> readParticleType(StringReader reader, HolderLookup<ParticleType<?>> registryWrapper) throws CommandSyntaxException {
        ResourceLocation resourceLocation = ResourceLocation.read(reader);
        ResourceKey<ParticleType<?>> resourceKey = ResourceKey.create(Registries.PARTICLE_TYPE, resourceLocation);
        return registryWrapper.get(resourceKey).orElseThrow(() -> ERROR_UNKNOWN_PARTICLE.createWithContext(reader, resourceLocation)).value();
    }

    private static <T extends ParticleOptions> T readParticle(StringReader reader, ParticleType<T> type, HolderLookup.Provider registryLookup) throws CommandSyntaxException {
        CompoundTag compoundTag;
        if (reader.canRead() && reader.peek() == '{') {
            compoundTag = new TagParser(reader).readStruct();
        } else {
            compoundTag = new CompoundTag();
        }

        return type.codec().codec().parse(registryLookup.createSerializationContext(NbtOps.INSTANCE), compoundTag).getOrThrow(ERROR_INVALID_OPTIONS::create);
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        HolderLookup.RegistryLookup<ParticleType<?>> registryLookup = this.registries.lookupOrThrow(Registries.PARTICLE_TYPE);
        return SharedSuggestionProvider.suggestResource(registryLookup.listElementIds().map(ResourceKey::location), suggestionsBuilder);
    }
}
