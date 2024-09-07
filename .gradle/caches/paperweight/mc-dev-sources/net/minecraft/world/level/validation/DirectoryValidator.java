package net.minecraft.world.level.validation;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class DirectoryValidator {
    private final PathMatcher symlinkTargetAllowList;

    public DirectoryValidator(PathMatcher matcher) {
        this.symlinkTargetAllowList = matcher;
    }

    public void validateSymlink(Path path, List<ForbiddenSymlinkInfo> results) throws IOException {
        Path path2 = Files.readSymbolicLink(path);
        if (!this.symlinkTargetAllowList.matches(path2)) {
            results.add(new ForbiddenSymlinkInfo(path, path2));
        }
    }

    public List<ForbiddenSymlinkInfo> validateSymlink(Path path) throws IOException {
        List<ForbiddenSymlinkInfo> list = new ArrayList<>();
        this.validateSymlink(path, list);
        return list;
    }

    public List<ForbiddenSymlinkInfo> validateDirectory(Path path, boolean resolveSymlink) throws IOException {
        List<ForbiddenSymlinkInfo> list = new ArrayList<>();

        BasicFileAttributes basicFileAttributes;
        try {
            basicFileAttributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException var6) {
            return list;
        }

        if (basicFileAttributes.isRegularFile()) {
            throw new IOException("Path " + path + " is not a directory");
        } else {
            if (basicFileAttributes.isSymbolicLink()) {
                if (!resolveSymlink) {
                    this.validateSymlink(path, list);
                    return list;
                }

                path = Files.readSymbolicLink(path);
            }

            this.validateKnownDirectory(path, list);
            return list;
        }
    }

    public void validateKnownDirectory(Path path, List<ForbiddenSymlinkInfo> results) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            private void validateSymlink(Path path, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink()) {
                    DirectoryValidator.this.validateSymlink(path, results);
                }
            }

            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                this.validateSymlink(path, basicFileAttributes);
                return super.preVisitDirectory(path, basicFileAttributes);
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                this.validateSymlink(path, basicFileAttributes);
                return super.visitFile(path, basicFileAttributes);
            }
        });
    }
}
