package net.minecraft.world.level.validation;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ContentValidationException extends Exception {
    private final Path directory;
    private final List<ForbiddenSymlinkInfo> entries;

    public ContentValidationException(Path path, List<ForbiddenSymlinkInfo> symlinks) {
        this.directory = path;
        this.entries = symlinks;
    }

    @Override
    public String getMessage() {
        return getMessage(this.directory, this.entries);
    }

    public static String getMessage(Path path, List<ForbiddenSymlinkInfo> symlinks) {
        return "Failed to validate '"
            + path
            + "'. Found forbidden symlinks: "
            + symlinks.stream().map(symlink -> symlink.link() + "->" + symlink.target()).collect(Collectors.joining(", "));
    }
}
