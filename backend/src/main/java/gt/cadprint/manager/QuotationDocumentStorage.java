package gt.cadprint.manager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import gt.cadprint.manager.QuotationDocumentQueue.Candidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class QuotationDocumentStorage {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private final Path root;

    QuotationDocumentStorage(@Value("${cadprint.quotation-documents.directory}") String directory) {
        Path configured = Path.of(directory);
        if (!configured.isAbsolute()) {
            throw new IllegalArgumentException("La ruta de PDF debe ser absoluta");
        }
        root = configured.normalize();
        for (Path cwd = Path.of("").toAbsolutePath(); cwd != null; cwd = cwd.getParent()) {
            if ((Files.exists(cwd.resolve("src/main/java")) || Files.exists(cwd.resolve("backend/src/main/java")))
                    && root.startsWith(cwd)) {
                throw new IllegalArgumentException("La ruta de PDF debe quedar fuera del proyecto");
            }
        }
    }

    Candidate stage(byte[] pdf) throws IOException {
        if (pdf.length == 0 || pdf.length > MAX_BYTES) {
            throw new IOException("Tamaño PDF inválido");
        }
        String key = UUID.randomUUID().toString();
        try (FileChannel file = FileChannel.open(path(key, ".part"), StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer content = ByteBuffer.wrap(pdf);
            while (content.hasRemaining()) {
                file.write(content);
            }
            file.force(true);
        }
        return new Candidate(key, hash(pdf), pdf.length);
    }

    boolean recover(Candidate candidate) throws IOException {
        Path original = path(candidate.key(), ".pdf");
        if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
            return matches(original, candidate);
        }

        Path temporary = path(candidate.key(), ".part");
        if (!matches(temporary, candidate)) {
            return false;
        }

        try {
            Files.createLink(original, temporary);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            // A concurrent recovery may have published the same candidate.
        } catch (IOException | UnsupportedOperationException linkUnavailable) {
            try {
                Files.copy(temporary, original, LinkOption.NOFOLLOW_LINKS);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // The destination is exclusive in both publication paths.
            }
        }
        if (!matches(original, candidate)) {
            return false;
        }
        Files.deleteIfExists(temporary);
        return true;
    }

    byte[] read(Candidate candidate) throws IOException {
        byte[] bytes = readRegular(path(candidate.key(), ".pdf"), candidate);
        if (!verified(bytes, candidate)) {
            throw new IOException("El archivo PDF original falta o no coincide con su huella");
        }
        return bytes;
    }

    private boolean matches(Path path, Candidate candidate) throws IOException {
        return verified(readRegular(path, candidate), candidate);
    }

    private byte[] readRegular(Path path, Candidate candidate) throws IOException {
        if (candidate.size() < 1 || candidate.size() > MAX_BYTES
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try (var source = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return source.readNBytes((int) candidate.size() + 1);
        } catch (java.nio.file.NoSuchFileException gone) {
            return null;
        }
    }

    private boolean verified(byte[] bytes, Candidate candidate) {
        return bytes != null && bytes.length == candidate.size() && hash(bytes).equals(candidate.sha256());
    }

    private Path path(String key, String extension) throws IOException {
        if (key == null || !key.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")) {
            throw new IOException("Clave documental inválida");
        }
        for (Path parent = root; parent != null; parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) {
                throw new IOException("La raíz documental contiene un enlace");
            }
        }
        Files.createDirectories(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root)) {
            throw new IOException("La raíz documental es inválida");
        }
        return root.resolve(key + extension);
    }

    static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
