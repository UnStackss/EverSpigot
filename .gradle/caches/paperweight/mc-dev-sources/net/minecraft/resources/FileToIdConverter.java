package net.minecraft.resources;

import java.util.List;
import java.util.Map;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public class FileToIdConverter {
    private final String prefix;
    private final String extension;

    public FileToIdConverter(String directoryName, String fileExtension) {
        this.prefix = directoryName;
        this.extension = fileExtension;
    }

    public static FileToIdConverter json(String directoryName) {
        return new FileToIdConverter(directoryName, ".json");
    }

    public ResourceLocation idToFile(ResourceLocation id) {
        return id.withPath(this.prefix + "/" + id.getPath() + this.extension);
    }

    public ResourceLocation fileToId(ResourceLocation path) {
        String string = path.getPath();
        return path.withPath(string.substring(this.prefix.length() + 1, string.length() - this.extension.length()));
    }

    public Map<ResourceLocation, Resource> listMatchingResources(ResourceManager resourceManager) {
        return resourceManager.listResources(this.prefix, path -> path.getPath().endsWith(this.extension));
    }

    public Map<ResourceLocation, List<Resource>> listMatchingResourceStacks(ResourceManager resourceManager) {
        return resourceManager.listResourceStacks(this.prefix, path -> path.getPath().endsWith(this.extension));
    }
}
