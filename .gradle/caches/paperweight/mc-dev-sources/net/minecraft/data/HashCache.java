package net.minecraft.data;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.WorldVersion;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;

public class HashCache {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final String HEADER_MARKER = "// ";
    private final Path rootDir;
    private final Path cacheDir;
    private final String versionId;
    private final Map<String, HashCache.ProviderCache> caches;
    private final Set<String> cachesToWrite = new HashSet<>();
    final Set<Path> cachePaths = new HashSet<>();
    private final int initialCount;
    private int writes;

    private Path getProviderCachePath(String providerName) {
        return this.cacheDir.resolve(Hashing.sha1().hashString(providerName, StandardCharsets.UTF_8).toString());
    }

    public HashCache(Path root, Collection<String> providerNames, WorldVersion gameVersion) throws IOException {
        this.versionId = gameVersion.getName();
        this.rootDir = root;
        this.cacheDir = root.resolve(".cache");
        Files.createDirectories(this.cacheDir);
        Map<String, HashCache.ProviderCache> map = new HashMap<>();
        int i = 0;

        for (String string : providerNames) {
            Path path = this.getProviderCachePath(string);
            this.cachePaths.add(path);
            HashCache.ProviderCache providerCache = readCache(root, path);
            map.put(string, providerCache);
            i += providerCache.count();
        }

        this.caches = map;
        this.initialCount = i;
    }

    private static HashCache.ProviderCache readCache(Path root, Path dataProviderPath) {
        if (Files.isReadable(dataProviderPath)) {
            try {
                return HashCache.ProviderCache.load(root, dataProviderPath);
            } catch (Exception var3) {
                LOGGER.warn("Failed to parse cache {}, discarding", dataProviderPath, var3);
            }
        }

        return new HashCache.ProviderCache("unknown", ImmutableMap.of());
    }

    public boolean shouldRunInThisVersion(String providerName) {
        HashCache.ProviderCache providerCache = this.caches.get(providerName);
        return providerCache == null || !providerCache.version.equals(this.versionId);
    }

    public CompletableFuture<HashCache.UpdateResult> generateUpdate(String providerName, HashCache.UpdateFunction runner) {
        HashCache.ProviderCache providerCache = this.caches.get(providerName);
        if (providerCache == null) {
            throw new IllegalStateException("Provider not registered: " + providerName);
        } else {
            HashCache.CacheUpdater cacheUpdater = new HashCache.CacheUpdater(providerName, this.versionId, providerCache);
            return runner.update(cacheUpdater).thenApply(void_ -> cacheUpdater.close());
        }
    }

    public void applyUpdate(HashCache.UpdateResult runResult) {
        this.caches.put(runResult.providerId(), runResult.cache());
        this.cachesToWrite.add(runResult.providerId());
        this.writes = this.writes + runResult.writes();
    }

    public void purgeStaleAndWrite() throws IOException {
        final Set<Path> set = new HashSet<>();
        this.caches.forEach((providerName, cachedData) -> {
            if (this.cachesToWrite.contains(providerName)) {
                Path path = this.getProviderCachePath(providerName);
                cachedData.save(this.rootDir, path, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()) + "\t" + providerName);
            }

            set.addAll(cachedData.data().keySet());
        });
        set.add(this.rootDir.resolve("version.json"));
        final MutableInt mutableInt = new MutableInt();
        final MutableInt mutableInt2 = new MutableInt();
        Files.walkFileTree(this.rootDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
                if (HashCache.this.cachePaths.contains(path)) {
                    return FileVisitResult.CONTINUE;
                } else {
                    mutableInt.increment();
                    if (set.contains(path)) {
                        return FileVisitResult.CONTINUE;
                    } else {
                        try {
                            Files.delete(path);
                        } catch (IOException var4) {
                            HashCache.LOGGER.warn("Failed to delete file {}", path, var4);
                        }

                        mutableInt2.increment();
                        return FileVisitResult.CONTINUE;
                    }
                }
            }
        });
        LOGGER.info(
            "Caching: total files: {}, old count: {}, new count: {}, removed stale: {}, written: {}",
            mutableInt,
            this.initialCount,
            set.size(),
            mutableInt2,
            this.writes
        );
    }

    class CacheUpdater implements CachedOutput {
        private final String provider;
        private final HashCache.ProviderCache oldCache;
        private final HashCache.ProviderCacheBuilder newCache;
        private final AtomicInteger writes = new AtomicInteger();
        private volatile boolean closed;

        CacheUpdater(final String providerName, final String version, final HashCache.ProviderCache oldCache) {
            this.provider = providerName;
            this.oldCache = oldCache;
            this.newCache = new HashCache.ProviderCacheBuilder(version);
        }

        private boolean shouldWrite(Path path, HashCode hashCode) {
            return !Objects.equals(this.oldCache.get(path), hashCode) || !Files.exists(path);
        }

        @Override
        public void writeIfNeeded(Path path, byte[] data, HashCode hashCode) throws IOException {
            if (this.closed) {
                throw new IllegalStateException("Cannot write to cache as it has already been closed");
            } else {
                if (this.shouldWrite(path, hashCode)) {
                    this.writes.incrementAndGet();
                    Files.createDirectories(path.getParent());
                    Files.write(path, data);
                }

                this.newCache.put(path, hashCode);
            }
        }

        public HashCache.UpdateResult close() {
            this.closed = true;
            return new HashCache.UpdateResult(this.provider, this.newCache.build(), this.writes.get());
        }
    }

    static record ProviderCache(String version, ImmutableMap<Path, HashCode> data) {
        @Nullable
        public HashCode get(Path path) {
            return this.data.get(path);
        }

        public int count() {
            return this.data.size();
        }

        public static HashCache.ProviderCache load(Path root, Path dataProviderPath) throws IOException {
            HashCache.ProviderCache var7;
            try (BufferedReader bufferedReader = Files.newBufferedReader(dataProviderPath, StandardCharsets.UTF_8)) {
                String string = bufferedReader.readLine();
                if (!string.startsWith("// ")) {
                    throw new IllegalStateException("Missing cache file header");
                }

                String[] strings = string.substring("// ".length()).split("\t", 2);
                String string2 = strings[0];
                Builder<Path, HashCode> builder = ImmutableMap.builder();
                bufferedReader.lines().forEach(line -> {
                    int i = line.indexOf(32);
                    builder.put(root.resolve(line.substring(i + 1)), HashCode.fromString(line.substring(0, i)));
                });
                var7 = new HashCache.ProviderCache(string2, builder.build());
            }

            return var7;
        }

        public void save(Path root, Path dataProviderPath, String description) {
            try (BufferedWriter bufferedWriter = Files.newBufferedWriter(dataProviderPath, StandardCharsets.UTF_8)) {
                bufferedWriter.write("// ");
                bufferedWriter.write(this.version);
                bufferedWriter.write(9);
                bufferedWriter.write(description);
                bufferedWriter.newLine();

                for (Entry<Path, HashCode> entry : this.data.entrySet()) {
                    bufferedWriter.write(entry.getValue().toString());
                    bufferedWriter.write(32);
                    bufferedWriter.write(root.relativize(entry.getKey()).toString());
                    bufferedWriter.newLine();
                }
            } catch (IOException var9) {
                HashCache.LOGGER.warn("Unable write cachefile {}: {}", dataProviderPath, var9);
            }
        }
    }

    static record ProviderCacheBuilder(String version, ConcurrentMap<Path, HashCode> data) {
        ProviderCacheBuilder(String version) {
            this(version, new ConcurrentHashMap<>());
        }

        public void put(Path path, HashCode hashCode) {
            this.data.put(path, hashCode);
        }

        public HashCache.ProviderCache build() {
            return new HashCache.ProviderCache(this.version, ImmutableMap.copyOf(this.data));
        }
    }

    @FunctionalInterface
    public interface UpdateFunction {
        CompletableFuture<?> update(CachedOutput writer);
    }

    public static record UpdateResult(String providerId, HashCache.ProviderCache cache, int writes) {
    }
}
