package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public abstract class BuiltInPackSource implements RepositorySource {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String VANILLA_ID = "vanilla";
    public static final KnownPack CORE_PACK_INFO = KnownPack.vanilla("core");
    private final PackType packType;
    private final VanillaPackResources vanillaPack;
    private final ResourceLocation packDir;
    private final DirectoryValidator validator;

    public BuiltInPackSource(PackType type, VanillaPackResources resourcePack, ResourceLocation id, DirectoryValidator symlinkFinder) {
        this.packType = type;
        this.vanillaPack = resourcePack;
        this.packDir = id;
        this.validator = symlinkFinder;
    }

    @Override
    public void loadPacks(Consumer<Pack> profileAdder) {
        Pack pack = this.createVanillaPack(this.vanillaPack);
        if (pack != null) {
            profileAdder.accept(pack);
        }

        this.listBundledPacks(profileAdder);
    }

    @Nullable
    protected abstract Pack createVanillaPack(PackResources pack);

    protected abstract Component getPackTitle(String id);

    public VanillaPackResources getVanillaPack() {
        return this.vanillaPack;
    }

    private void listBundledPacks(Consumer<Pack> consumer) {
        Map<String, Function<String, Pack>> map = new HashMap<>();
        this.populatePackList(map::put);
        map.forEach((id, packFactory) -> {
            Pack pack = packFactory.apply(id);
            if (pack != null) {
                consumer.accept(pack);
            }
        });
    }

    protected void populatePackList(BiConsumer<String, Function<String, Pack>> consumer) {
        this.vanillaPack.listRawPaths(this.packType, this.packDir, namespacedPath -> this.discoverPacksInPath(namespacedPath, consumer));
    }

    protected void discoverPacksInPath(@Nullable Path namespacedPath, BiConsumer<String, Function<String, Pack>> consumer) {
        if (namespacedPath != null && Files.isDirectory(namespacedPath)) {
            try {
                FolderRepositorySource.discoverPacks(
                    namespacedPath,
                    this.validator,
                    (profilePath, factory) -> consumer.accept(pathToId(profilePath), id -> this.createBuiltinPack(id, factory, this.getPackTitle(id)))
                );
            } catch (IOException var4) {
                LOGGER.warn("Failed to discover packs in {}", namespacedPath, var4);
            }
        }
    }

    private static String pathToId(Path path) {
        return StringUtils.removeEnd(path.getFileName().toString(), ".zip");
    }

    @Nullable
    protected abstract Pack createBuiltinPack(String fileName, Pack.ResourcesSupplier packFactory, Component displayName);

    protected static Pack.ResourcesSupplier fixedResources(PackResources pack) {
        return new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return pack;
            }

            @Override
            public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return pack;
            }
        };
    }
}
