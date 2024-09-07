package net.minecraft.data;

import java.nio.file.Path;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class PackOutput {
    private final Path outputFolder;

    public PackOutput(Path path) {
        this.outputFolder = path;
    }

    public Path getOutputFolder() {
        return this.outputFolder;
    }

    public Path getOutputFolder(PackOutput.Target outputType) {
        return this.getOutputFolder().resolve(outputType.directory);
    }

    public PackOutput.PathProvider createPathProvider(PackOutput.Target outputType, String directoryName) {
        return new PackOutput.PathProvider(this, outputType, directoryName);
    }

    public PackOutput.PathProvider createRegistryElementsPathProvider(ResourceKey<? extends Registry<?>> registryRef) {
        return this.createPathProvider(PackOutput.Target.DATA_PACK, Registries.elementsDirPath(registryRef));
    }

    public PackOutput.PathProvider createRegistryTagsPathProvider(ResourceKey<? extends Registry<?>> registryRef) {
        return this.createPathProvider(PackOutput.Target.DATA_PACK, Registries.tagsDirPath(registryRef));
    }

    public static class PathProvider {
        private final Path root;
        private final String kind;

        PathProvider(PackOutput dataGenerator, PackOutput.Target outputType, String directoryName) {
            this.root = dataGenerator.getOutputFolder(outputType);
            this.kind = directoryName;
        }

        public Path file(ResourceLocation id, String fileExtension) {
            return this.root.resolve(id.getNamespace()).resolve(this.kind).resolve(id.getPath() + "." + fileExtension);
        }

        public Path json(ResourceLocation id) {
            return this.root.resolve(id.getNamespace()).resolve(this.kind).resolve(id.getPath() + ".json");
        }
    }

    public static enum Target {
        DATA_PACK("data"),
        RESOURCE_PACK("assets"),
        REPORTS("reports");

        final String directory;

        private Target(final String path) {
            this.directory = path;
        }
    }
}
