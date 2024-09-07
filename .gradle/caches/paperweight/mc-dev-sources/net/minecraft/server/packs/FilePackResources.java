package net.minecraft.server.packs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class FilePackResources extends AbstractPackResources {
    static final Logger LOGGER = LogUtils.getLogger();
    private final FilePackResources.SharedZipFileAccess zipFileAccess;
    private final String prefix;

    FilePackResources(PackLocationInfo info, FilePackResources.SharedZipFileAccess zipFile, String overlay) {
        super(info);
        this.zipFileAccess = zipFile;
        this.prefix = overlay;
    }

    private static String getPathFromLocation(PackType type, ResourceLocation id) {
        return String.format(Locale.ROOT, "%s/%s/%s", type.getDirectory(), id.getNamespace(), id.getPath());
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... segments) {
        return this.getResource(String.join("/", segments));
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        return this.getResource(getPathFromLocation(type, id));
    }

    private String addPrefix(String path) {
        return this.prefix.isEmpty() ? path : this.prefix + "/" + path;
    }

    @Nullable
    private IoSupplier<InputStream> getResource(String path) {
        ZipFile zipFile = this.zipFileAccess.getOrCreateZipFile();
        if (zipFile == null) {
            return null;
        } else {
            ZipEntry zipEntry = zipFile.getEntry(this.addPrefix(path));
            return zipEntry == null ? null : IoSupplier.create(zipFile, zipEntry);
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        ZipFile zipFile = this.zipFileAccess.getOrCreateZipFile();
        if (zipFile == null) {
            return Set.of();
        } else {
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            Set<String> set = Sets.newHashSet();
            String string = this.addPrefix(type.getDirectory() + "/");

            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                String string2 = zipEntry.getName();
                String string3 = extractNamespace(string, string2);
                if (!string3.isEmpty()) {
                    if (ResourceLocation.isValidNamespace(string3)) {
                        set.add(string3);
                    } else {
                        LOGGER.warn("Non [a-z0-9_.-] character in namespace {} in pack {}, ignoring", string3, this.zipFileAccess.file);
                    }
                }
            }

            return set;
        }
    }

    @VisibleForTesting
    public static String extractNamespace(String prefix, String entryName) {
        if (!entryName.startsWith(prefix)) {
            return "";
        } else {
            int i = prefix.length();
            int j = entryName.indexOf(47, i);
            return j == -1 ? entryName.substring(i) : entryName.substring(i, j);
        }
    }

    @Override
    public void close() {
        this.zipFileAccess.close();
    }

    @Override
    public void listResources(PackType type, String namespace, String prefix, PackResources.ResourceOutput consumer) {
        ZipFile zipFile = this.zipFileAccess.getOrCreateZipFile();
        if (zipFile != null) {
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            String string = this.addPrefix(type.getDirectory() + "/" + namespace + "/");
            String string2 = string + prefix + "/";

            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                if (!zipEntry.isDirectory()) {
                    String string3 = zipEntry.getName();
                    if (string3.startsWith(string2)) {
                        String string4 = string3.substring(string.length());
                        ResourceLocation resourceLocation = ResourceLocation.tryBuild(namespace, string4);
                        if (resourceLocation != null) {
                            consumer.accept(resourceLocation, IoSupplier.create(zipFile, zipEntry));
                        } else {
                            LOGGER.warn("Invalid path in datapack: {}:{}, ignoring", namespace, string4);
                        }
                    }
                }
            }
        }
    }

    public static class FileResourcesSupplier implements Pack.ResourcesSupplier {
        private final File content;

        public FileResourcesSupplier(Path path) {
            this(path.toFile());
        }

        public FileResourcesSupplier(File file) {
            this.content = file;
        }

        @Override
        public PackResources openPrimary(PackLocationInfo info) {
            FilePackResources.SharedZipFileAccess sharedZipFileAccess = new FilePackResources.SharedZipFileAccess(this.content);
            return new FilePackResources(info, sharedZipFileAccess, "");
        }

        @Override
        public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
            FilePackResources.SharedZipFileAccess sharedZipFileAccess = new FilePackResources.SharedZipFileAccess(this.content);
            PackResources packResources = new FilePackResources(info, sharedZipFileAccess, "");
            List<String> list = metadata.overlays();
            if (list.isEmpty()) {
                return packResources;
            } else {
                List<PackResources> list2 = new ArrayList<>(list.size());

                for (String string : list) {
                    list2.add(new FilePackResources(info, sharedZipFileAccess, string));
                }

                return new CompositePackResources(packResources, list2);
            }
        }
    }

    static class SharedZipFileAccess implements AutoCloseable {
        final File file;
        @Nullable
        private ZipFile zipFile;
        private boolean failedToLoad;

        SharedZipFileAccess(File file) {
            this.file = file;
        }

        @Nullable
        ZipFile getOrCreateZipFile() {
            if (this.failedToLoad) {
                return null;
            } else {
                if (this.zipFile == null) {
                    try {
                        this.zipFile = new ZipFile(this.file);
                    } catch (IOException var2) {
                        FilePackResources.LOGGER.error("Failed to open pack {}", this.file, var2);
                        this.failedToLoad = true;
                        return null;
                    }
                }

                return this.zipFile;
            }
        }

        @Override
        public void close() {
            if (this.zipFile != null) {
                IOUtils.closeQuietly(this.zipFile);
                this.zipFile = null;
            }
        }

        @Override
        protected void finalize() throws Throwable {
            this.close();
            super.finalize();
        }
    }
}
