package gt.cadprint.manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LocalProductImageStorage implements ProductImageStorage {
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private final Path configuredRoot;

    LocalProductImageStorage(@Value("${cadprint.product-images.directory}") String directory) {
        configuredRoot = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] image) throws IOException {
        String key = UUID.randomUUID().toString();
        Path file = resolve(key);
        // A failure before opening leaves any pre-existing file untouched.
        var output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        try (output) {
            output.write(image);
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        return key;
    }

    @Override
    public byte[] read(String key) throws IOException {
        try (var input = Files.newInputStream(resolve(key), LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(ProductImageValidator.MAX_BYTES + 1);
            if (bytes.length > ProductImageValidator.MAX_BYTES) {
                throw new IOException("Stored image exceeds the configured size limit");
            }
            return bytes;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) throws IOException {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IOException("Invalid image storage key");
        }
        Files.createDirectories(configuredRoot);
        if (!Files.isDirectory(configuredRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Image storage directory is invalid");
        }
        return configuredRoot.toRealPath().resolve(key);
    }
}
