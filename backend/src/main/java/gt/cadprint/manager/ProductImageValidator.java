package gt.cadprint.manager;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
class ProductImageValidator {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    record ValidatedImage(byte[] bytes, String contentType) {
    }

    ValidatedImage validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw invalid("El archivo está vacío");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen supera 5 MiB");
        }
        byte[] bytes;
        try (var source = file.getInputStream()) {
            bytes = source.readNBytes(MAX_BYTES + 1);
        } catch (IOException exception) {
            throw invalid("No se pudo leer la imagen");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen supera 5 MiB");
        }

        String format;
        if (Arrays.equals(bytes, 0, Math.min(bytes.length, 8), PNG_SIGNATURE, 0, 8)) {
            format = "png";
        } else if (bytes.length >= 3 && unsigned(bytes[0]) == 255 && unsigned(bytes[1]) == 216
                && unsigned(bytes[2]) == 255) {
            format = "jpeg";
        } else {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo se aceptan imágenes JPEG y PNG");
        }
        validateEncoding(bytes, format);
        validateDecodedImage(bytes, format);
        return new ValidatedImage(bytes, "image/" + format);
    }

    private void validateEncoding(byte[] bytes, String format) {
        if (format.equals("jpeg")) {
            if (unsigned(bytes[bytes.length - 2]) != 255 || unsigned(bytes[bytes.length - 1]) != 217) {
                throw invalid("La imagen JPEG está incompleta");
            }
            return;
        }
        var buffer = ByteBuffer.wrap(bytes);
        buffer.position(PNG_SIGNATURE.length);
        while (buffer.remaining() >= 12) {
            int length = buffer.getInt();
            int typeOffset = buffer.position();
            int type = buffer.getInt();
            if (length < 0 || length > buffer.remaining() - Integer.BYTES) {
                break;
            }
            var crc = new CRC32();
            crc.update(bytes, typeOffset, Integer.BYTES + length);
            buffer.position(buffer.position() + length);
            if (Integer.toUnsignedLong(buffer.getInt()) != crc.getValue()) {
                break;
            }
            if (type == 0x49454e44 && length == 0 && !buffer.hasRemaining()) { // IEND
                return;
            }
        }
        throw invalid("La estructura PNG está incompleta o dañada");
    }

    private void validateDecodedImage(byte[] bytes, String format) {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReadersByFormatName(format);
            if (!readers.hasNext()) {
                throw invalid("El formato de imagen no se pudo leer");
            }
            ImageReader reader = readers.next();
            try {
                var warning = new AtomicBoolean();
                reader.addIIOReadWarningListener((source, message) -> warning.set(true));
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw invalid("La imagen debe tener como máximo 20 megapíxeles");
                }
                var parameters = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(width, height) / 1024);
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage decoded = reader.read(0, parameters);
                if (decoded == null || warning.get()) {
                    throw invalid("La imagen está dañada");
                }
                decoded.flush();
            } finally {
                reader.dispose();
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid("La imagen está dañada");
        }
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
