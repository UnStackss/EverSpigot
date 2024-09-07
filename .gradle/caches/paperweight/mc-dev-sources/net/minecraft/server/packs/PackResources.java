package net.minecraft.server.packs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.IoSupplier;

public interface PackResources extends AutoCloseable {
    String METADATA_EXTENSION = ".mcmeta";
    String PACK_META = "pack.mcmeta";

    @Nullable
    IoSupplier<InputStream> getRootResource(String... segments);

    @Nullable
    IoSupplier<InputStream> getResource(PackType type, ResourceLocation id);

    void listResources(PackType type, String namespace, String prefix, PackResources.ResourceOutput consumer);

    Set<String> getNamespaces(PackType type);

    @Nullable
    <T> T getMetadataSection(MetadataSectionSerializer<T> metaReader) throws IOException;

    PackLocationInfo location();

    default String packId() {
        return this.location().id();
    }

    default Optional<KnownPack> knownPackInfo() {
        return this.location().knownPackInfo();
    }

    @Override
    void close();

    @FunctionalInterface
    public interface ResourceOutput extends BiConsumer<ResourceLocation, IoSupplier<InputStream>> {
    }
}
