package net.minecraft.server.packs;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

public class DownloadCacheCleaner {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void vacuumCacheDir(Path directory, int maxRetained) {
        try {
            List<DownloadCacheCleaner.PathAndTime> list = listFilesWithModificationTimes(directory);
            int i = list.size() - maxRetained;
            if (i <= 0) {
                return;
            }

            list.sort(DownloadCacheCleaner.PathAndTime.NEWEST_FIRST);
            List<DownloadCacheCleaner.PathAndPriority> list2 = prioritizeFilesInDirs(list);
            Collections.reverse(list2);
            list2.sort(DownloadCacheCleaner.PathAndPriority.HIGHEST_PRIORITY_FIRST);
            Set<Path> set = new HashSet<>();

            for (int j = 0; j < i; j++) {
                DownloadCacheCleaner.PathAndPriority pathAndPriority = list2.get(j);
                Path path = pathAndPriority.path;

                try {
                    Files.delete(path);
                    if (pathAndPriority.removalPriority == 0) {
                        set.add(path.getParent());
                    }
                } catch (IOException var12) {
                    LOGGER.warn("Failed to delete cache file {}", path, var12);
                }
            }

            set.remove(directory);

            for (Path path2 : set) {
                try {
                    Files.delete(path2);
                } catch (DirectoryNotEmptyException var10) {
                } catch (IOException var11) {
                    LOGGER.warn("Failed to delete empty(?) cache directory {}", path2, var11);
                }
            }
        } catch (UncheckedIOException | IOException var13) {
            LOGGER.error("Failed to vacuum cache dir {}", directory, var13);
        }
    }

    private static List<DownloadCacheCleaner.PathAndTime> listFilesWithModificationTimes(Path directory) throws IOException {
        try {
            final List<DownloadCacheCleaner.PathAndTime> list = new ArrayList<>();
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
                    if (basicFileAttributes.isRegularFile() && !path.getParent().equals(directory)) {
                        FileTime fileTime = basicFileAttributes.lastModifiedTime();
                        list.add(new DownloadCacheCleaner.PathAndTime(path, fileTime));
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
            return list;
        } catch (NoSuchFileException var2) {
            return List.of();
        }
    }

    private static List<DownloadCacheCleaner.PathAndPriority> prioritizeFilesInDirs(List<DownloadCacheCleaner.PathAndTime> files) {
        List<DownloadCacheCleaner.PathAndPriority> list = new ArrayList<>();
        Object2IntOpenHashMap<Path> object2IntOpenHashMap = new Object2IntOpenHashMap<>();

        for (DownloadCacheCleaner.PathAndTime pathAndTime : files) {
            int i = object2IntOpenHashMap.addTo(pathAndTime.path.getParent(), 1);
            list.add(new DownloadCacheCleaner.PathAndPriority(pathAndTime.path, i));
        }

        return list;
    }

    static record PathAndPriority(Path path, int removalPriority) {
        public static final Comparator<DownloadCacheCleaner.PathAndPriority> HIGHEST_PRIORITY_FIRST = Comparator.comparing(
                DownloadCacheCleaner.PathAndPriority::removalPriority
            )
            .reversed();
    }

    static record PathAndTime(Path path, FileTime modifiedTime) {
        public static final Comparator<DownloadCacheCleaner.PathAndTime> NEWEST_FIRST = Comparator.comparing(DownloadCacheCleaner.PathAndTime::modifiedTime)
            .reversed();
    }
}
