package net.minecraft.server.packs;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.Util;
import org.slf4j.Logger;

public class VanillaPackResourcesBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static Consumer<VanillaPackResourcesBuilder> developmentConfig = builder -> {
    };
    private static final Map<PackType, Path> ROOT_DIR_BY_TYPE = Util.make(() -> {
        synchronized (VanillaPackResources.class) {
            Builder<PackType, Path> builder = ImmutableMap.builder();

            for (PackType packType : PackType.values()) {
                String string = "/" + packType.getDirectory() + "/.mcassetsroot";
                URL uRL = VanillaPackResources.class.getResource(string);
                if (uRL == null) {
                    LOGGER.error("File {} does not exist in classpath", string);
                } else {
                    try {
                        URI uRI = uRL.toURI();
                        String string2 = uRI.getScheme();
                        if (!"jar".equals(string2) && !"file".equals(string2)) {
                            LOGGER.warn("Assets URL '{}' uses unexpected schema", uRI);
                        }

                        Path path = safeGetPath(uRI);
                        builder.put(packType, path.getParent());
                    } catch (Exception var12) {
                        LOGGER.error("Couldn't resolve path to vanilla assets", (Throwable)var12);
                    }
                }
            }

            return builder.build();
        }
    });
    private final Set<Path> rootPaths = new LinkedHashSet<>();
    private final Map<PackType, Set<Path>> pathsForType = new EnumMap<>(PackType.class);
    private BuiltInMetadata metadata = BuiltInMetadata.of();
    private final Set<String> namespaces = new HashSet<>();

    public static Path safeGetPath(URI uri) throws IOException {
        try {
            return Paths.get(uri);
        } catch (FileSystemNotFoundException var3) {
        } catch (Throwable var4) {
            LOGGER.warn("Unable to get path for: {}", uri, var4);
        }

        try {
            FileSystems.newFileSystem(uri, Collections.emptyMap());
        } catch (FileSystemAlreadyExistsException var2) {
        }

        return Paths.get(uri);
    }

    private boolean validateDirPath(Path path) {
        if (!Files.exists(path)) {
            return false;
        } else if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path " + path.toAbsolutePath() + " is not directory");
        } else {
            return true;
        }
    }

    private void pushRootPath(Path path) {
        if (this.validateDirPath(path)) {
            this.rootPaths.add(path);
        }
    }

    private void pushPathForType(PackType type, Path path) {
        if (this.validateDirPath(path)) {
            this.pathsForType.computeIfAbsent(type, type2 -> new LinkedHashSet<>()).add(path);
        }
    }

    public VanillaPackResourcesBuilder pushJarResources() {
        ROOT_DIR_BY_TYPE.forEach((type, path) -> {
            this.pushRootPath(path.getParent());
            this.pushPathForType(type, path);
        });
        return this;
    }

    public VanillaPackResourcesBuilder pushClasspathResources(PackType type, Class<?> clazz) {
        Enumeration<URL> enumeration = null;

        try {
            enumeration = clazz.getClassLoader().getResources(type.getDirectory() + "/");
        } catch (IOException var8) {
        }

        while (enumeration != null && enumeration.hasMoreElements()) {
            URL uRL = enumeration.nextElement();

            try {
                URI uRI = uRL.toURI();
                if ("file".equals(uRI.getScheme())) {
                    Path path = Paths.get(uRI);
                    this.pushRootPath(path.getParent());
                    this.pushPathForType(type, path);
                }
            } catch (Exception var7) {
                LOGGER.error("Failed to extract path from {}", uRL, var7);
            }
        }

        return this;
    }

    public VanillaPackResourcesBuilder applyDevelopmentConfig() {
        developmentConfig.accept(this);
        if (Boolean.getBoolean("Paper.pushPaperAssetsRoot")) {
            try {
                this.pushAssetPath(net.minecraft.server.packs.PackType.SERVER_DATA, net.minecraft.server.packs.VanillaPackResourcesBuilder.safeGetPath(java.util.Objects.requireNonNull(
                    // Important that this is a patched class
                    VanillaPackResourcesBuilder.class.getResource("/data/.paperassetsroot"), "Missing required .paperassetsroot file").toURI()).getParent());
            } catch (java.net.URISyntaxException | IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        return this;
    }

    public VanillaPackResourcesBuilder pushUniversalPath(Path root) {
        this.pushRootPath(root);

        for (PackType packType : PackType.values()) {
            this.pushPathForType(packType, root.resolve(packType.getDirectory()));
        }

        return this;
    }

    public VanillaPackResourcesBuilder pushAssetPath(PackType type, Path path) {
        this.pushRootPath(path);
        this.pushPathForType(type, path);
        return this;
    }

    public VanillaPackResourcesBuilder setMetadata(BuiltInMetadata metadataMap) {
        this.metadata = metadataMap;
        return this;
    }

    public VanillaPackResourcesBuilder exposeNamespace(String... namespaces) {
        this.namespaces.addAll(Arrays.asList(namespaces));
        return this;
    }

    public VanillaPackResources build(PackLocationInfo info) {
        Map<PackType, List<Path>> map = new EnumMap<>(PackType.class);

        for (PackType packType : PackType.values()) {
            List<Path> list = copyAndReverse(this.pathsForType.getOrDefault(packType, Set.of()));
            map.put(packType, list);
        }

        return new VanillaPackResources(info, this.metadata, Set.copyOf(this.namespaces), copyAndReverse(this.rootPaths), map);
    }

    private static List<Path> copyAndReverse(Collection<Path> paths) {
        List<Path> list = new ArrayList<>(paths);
        Collections.reverse(list);
        return List.copyOf(list);
    }
}
