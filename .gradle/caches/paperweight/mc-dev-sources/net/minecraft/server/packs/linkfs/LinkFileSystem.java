package net.minecraft.server.packs.linkfs;

import com.google.common.base.Splitter;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class LinkFileSystem extends FileSystem {
    private static final Set<String> VIEWS = Set.of("basic");
    public static final String PATH_SEPARATOR = "/";
    private static final Splitter PATH_SPLITTER = Splitter.on('/');
    private final FileStore store;
    private final FileSystemProvider provider = new LinkFSProvider();
    private final LinkFSPath root;

    LinkFileSystem(String name, LinkFileSystem.DirectoryEntry root) {
        this.store = new LinkFSFileStore(name);
        this.root = buildPath(root, this, "", null);
    }

    private static LinkFSPath buildPath(LinkFileSystem.DirectoryEntry root, LinkFileSystem fileSystem, String name, @Nullable LinkFSPath parent) {
        Object2ObjectOpenHashMap<String, LinkFSPath> object2ObjectOpenHashMap = new Object2ObjectOpenHashMap<>();
        LinkFSPath linkFSPath = new LinkFSPath(fileSystem, name, parent, new PathContents.DirectoryContents(object2ObjectOpenHashMap));
        root.files
            .forEach(
                (fileName, path) -> object2ObjectOpenHashMap.put(
                        fileName, new LinkFSPath(fileSystem, fileName, linkFSPath, new PathContents.FileContents(path))
                    )
            );
        root.children
            .forEach((directoryName, directory) -> object2ObjectOpenHashMap.put(directoryName, buildPath(directory, fileSystem, directoryName, linkFSPath)));
        object2ObjectOpenHashMap.trim();
        return linkFSPath;
    }

    @Override
    public FileSystemProvider provider() {
        return this.provider;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return List.of(this.root);
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return List.of(this.store);
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return VIEWS;
    }

    @Override
    public Path getPath(String string, String... strings) {
        Stream<String> stream = Stream.of(string);
        if (strings.length > 0) {
            stream = Stream.concat(stream, Stream.of(strings));
        }

        String string2 = stream.collect(Collectors.joining("/"));
        if (string2.equals("/")) {
            return this.root;
        } else if (string2.startsWith("/")) {
            LinkFSPath linkFSPath = this.root;

            for (String string3 : PATH_SPLITTER.split(string2.substring(1))) {
                if (string3.isEmpty()) {
                    throw new IllegalArgumentException("Empty paths not allowed");
                }

                linkFSPath = linkFSPath.resolveName(string3);
            }

            return linkFSPath;
        } else {
            LinkFSPath linkFSPath2 = null;

            for (String string4 : PATH_SPLITTER.split(string2)) {
                if (string4.isEmpty()) {
                    throw new IllegalArgumentException("Empty paths not allowed");
                }

                linkFSPath2 = new LinkFSPath(this, string4, linkFSPath2, PathContents.RELATIVE);
            }

            if (linkFSPath2 == null) {
                throw new IllegalArgumentException("Empty paths not allowed");
            } else {
                return linkFSPath2;
            }
        }
    }

    @Override
    public PathMatcher getPathMatcher(String string) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    public FileStore store() {
        return this.store;
    }

    public LinkFSPath rootPath() {
        return this.root;
    }

    public static LinkFileSystem.Builder builder() {
        return new LinkFileSystem.Builder();
    }

    public static class Builder {
        private final LinkFileSystem.DirectoryEntry root = new LinkFileSystem.DirectoryEntry();

        public LinkFileSystem.Builder put(List<String> directories, String name, Path path) {
            LinkFileSystem.DirectoryEntry directoryEntry = this.root;

            for (String string : directories) {
                directoryEntry = directoryEntry.children.computeIfAbsent(string, directory -> new LinkFileSystem.DirectoryEntry());
            }

            directoryEntry.files.put(name, path);
            return this;
        }

        public LinkFileSystem.Builder put(List<String> directories, Path path) {
            if (directories.isEmpty()) {
                throw new IllegalArgumentException("Path can't be empty");
            } else {
                int i = directories.size() - 1;
                return this.put(directories.subList(0, i), directories.get(i), path);
            }
        }

        public FileSystem build(String name) {
            return new LinkFileSystem(name, this.root);
        }
    }

    static record DirectoryEntry(Map<String, LinkFileSystem.DirectoryEntry> children, Map<String, Path> files) {
        public DirectoryEntry() {
            this(new HashMap<>(), new HashMap<>());
        }
    }
}
