package net.minecraft.util;

import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class HttpUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    private HttpUtil() {
    }

    public static Path downloadFile(
        Path path,
        URL url,
        Map<String, String> headers,
        HashFunction hashFunction,
        @Nullable HashCode hashCode,
        int maxBytes,
        Proxy proxy,
        HttpUtil.DownloadProgressListener listener
    ) {
        HttpURLConnection httpURLConnection = null;
        InputStream inputStream = null;
        listener.requestStart();
        Path path2;
        if (hashCode != null) {
            path2 = cachedFilePath(path, hashCode);

            try {
                if (checkExistingFile(path2, hashFunction, hashCode)) {
                    LOGGER.info("Returning cached file since actual hash matches requested");
                    listener.requestFinished(true);
                    updateModificationTime(path2);
                    return path2;
                }
            } catch (IOException var35) {
                LOGGER.warn("Failed to check cached file {}", path2, var35);
            }

            try {
                LOGGER.warn("Existing file {} not found or had mismatched hash", path2);
                Files.deleteIfExists(path2);
            } catch (IOException var34) {
                listener.requestFinished(false);
                throw new UncheckedIOException("Failed to remove existing file " + path2, var34);
            }
        } else {
            path2 = null;
        }

        Path hashCode3;
        try {
            httpURLConnection = (HttpURLConnection)url.openConnection(proxy);
            httpURLConnection.setInstanceFollowRedirects(true);
            headers.forEach(httpURLConnection::setRequestProperty);
            inputStream = httpURLConnection.getInputStream();
            long l = httpURLConnection.getContentLengthLong();
            OptionalLong optionalLong = l != -1L ? OptionalLong.of(l) : OptionalLong.empty();
            FileUtil.createDirectoriesSafe(path);
            listener.downloadStart(optionalLong);
            if (optionalLong.isPresent() && optionalLong.getAsLong() > (long)maxBytes) {
                throw new IOException("Filesize is bigger than maximum allowed (file is " + optionalLong + ", limit is " + maxBytes + ")");
            }

            if (path2 == null) {
                Path path4 = Files.createTempFile(path, "download", ".tmp");

                try {
                    HashCode hashCode3x = downloadAndHash(hashFunction, maxBytes, listener, inputStream, path4);
                    Path path5 = cachedFilePath(path, hashCode3x);
                    if (!checkExistingFile(path5, hashFunction, hashCode3x)) {
                        Files.move(path4, path5, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        updateModificationTime(path5);
                    }

                    listener.requestFinished(true);
                    return path5;
                } finally {
                    Files.deleteIfExists(path4);
                }
            }

            HashCode hashCode2 = downloadAndHash(hashFunction, maxBytes, listener, inputStream, path2);
            if (!hashCode2.equals(hashCode)) {
                throw new IOException("Hash of downloaded file (" + hashCode2 + ") did not match requested (" + hashCode + ")");
            }

            listener.requestFinished(true);
            hashCode3 = path2;
        } catch (Throwable var36) {
            if (httpURLConnection != null) {
                InputStream inputStream2 = httpURLConnection.getErrorStream();
                if (inputStream2 != null) {
                    try {
                        LOGGER.error("HTTP response error: {}", IOUtils.toString(inputStream2, StandardCharsets.UTF_8));
                    } catch (Exception var32) {
                        LOGGER.error("Failed to read response from server");
                    }
                }
            }

            listener.requestFinished(false);
            throw new IllegalStateException("Failed to download file " + url, var36);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }

        return hashCode3;
    }

    private static void updateModificationTime(Path path) {
        try {
            Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
        } catch (IOException var2) {
            LOGGER.warn("Failed to update modification time of {}", path, var2);
        }
    }

    private static HashCode hashFile(Path path, HashFunction hashFunction) throws IOException {
        Hasher hasher = hashFunction.newHasher();

        try (
            OutputStream outputStream = Funnels.asOutputStream(hasher);
            InputStream inputStream = Files.newInputStream(path);
        ) {
            inputStream.transferTo(outputStream);
        }

        return hasher.hash();
    }

    private static boolean checkExistingFile(Path path, HashFunction hashFunction, HashCode hashCode) throws IOException {
        if (Files.exists(path)) {
            HashCode hashCode2 = hashFile(path, hashFunction);
            if (hashCode2.equals(hashCode)) {
                return true;
            }

            LOGGER.warn("Mismatched hash of file {}, expected {} but found {}", path, hashCode, hashCode2);
        }

        return false;
    }

    private static Path cachedFilePath(Path path, HashCode hashCode) {
        return path.resolve(hashCode.toString());
    }

    private static HashCode downloadAndHash(HashFunction hashFunction, int maxBytes, HttpUtil.DownloadProgressListener listener, InputStream stream, Path path) throws IOException {
        HashCode var11;
        try (OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE)) {
            Hasher hasher = hashFunction.newHasher();
            byte[] bs = new byte[8196];
            long l = 0L;

            int i;
            while ((i = stream.read(bs)) >= 0) {
                l += (long)i;
                listener.downloadedBytes(l);
                if (l > (long)maxBytes) {
                    throw new IOException("Filesize was bigger than maximum allowed (got >= " + l + ", limit was " + maxBytes + ")");
                }

                if (Thread.interrupted()) {
                    LOGGER.error("INTERRUPTED");
                    throw new IOException("Download interrupted");
                }

                outputStream.write(bs, 0, i);
                hasher.putBytes(bs, 0, i);
            }

            var11 = hasher.hash();
        }

        return var11;
    }

    public static int getAvailablePort() {
        try {
            int var1;
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                var1 = serverSocket.getLocalPort();
            }

            return var1;
        } catch (IOException var5) {
            return 25564;
        }
    }

    public static boolean isPortAvailable(int port) {
        if (port >= 0 && port <= 65535) {
            try {
                boolean var2;
                try (ServerSocket serverSocket = new ServerSocket(port)) {
                    var2 = serverSocket.getLocalPort() == port;
                }

                return var2;
            } catch (IOException var6) {
                return false;
            }
        } else {
            return false;
        }
    }

    public interface DownloadProgressListener {
        void requestStart();

        void downloadStart(OptionalLong contentLength);

        void downloadedBytes(long writtenBytes);

        void requestFinished(boolean success);
    }
}
