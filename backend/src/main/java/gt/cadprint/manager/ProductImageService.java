package gt.cadprint.manager;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class ProductImageService {
    record ImageView(String contentType, long fileSize, Instant createdAt) {
        static ImageView from(ProductImage image) {
            return new ImageView(image.contentType, image.fileSize, image.createdAt);
        }
    }

    record ImageUpdate(ImageView image, Long version) {
    }

    record ImageBytes(byte[] bytes, String contentType) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(ProductImageService.class);
    private final ProductRepo products;
    private final ProductImageRepo images;
    private final ProductImageStorage storage;
    private final ProductImageValidator validator;
    private final EntityManager entityManager;

    ProductImageService(ProductRepo products, ProductImageRepo images, ProductImageStorage storage,
                        ProductImageValidator validator, EntityManager entityManager) {
        this.products = products;
        this.images = images;
        this.storage = storage;
        this.validator = validator;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    ImageView metadata(Long productId) {
        return images.findByProductId(productId).map(ImageView::from).orElse(null);
    }

    @Transactional
    ImageBytes read(Long productId) {
        // Hold the same row lock until the file has been read into memory.
        lockProduct(productId);
        ProductImage image = images.findByProductId(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El producto no tiene imagen"));
        try {
            byte[] content = storage.read(image.storageKey);
            if (content.length != image.fileSize) {
                throw new IOException("Stored image differs from its recorded size");
            }
            return new ImageBytes(content, image.contentType);
        } catch (IOException failure) {
            throw storageError(failure);
        }
    }

    @Transactional
    ImageUpdate put(Long productId, Long version, MultipartFile file) {
        Product product = lockProduct(productId);
        checkVersion(product, version);
        var validated = validator.validate(file);
        ProductImage image = images.findByProductId(productId).orElseGet(ProductImage::new);
        String oldKey = image.storageKey;
        String newKey;
        try {
            newKey = storage.store(validated.bytes());
        } catch (IOException failure) {
            throw storageError(failure);
        }
        registerCleanup(oldKey, newKey);
        image.product = product;
        image.storageKey = newKey;
        image.contentType = validated.contentType();
        image.fileSize = validated.bytes().length;
        image.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        entityManager.lock(product, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        images.saveAndFlush(image);
        return new ImageUpdate(ImageView.from(image), product.version);
    }

    @Transactional
    ImageUpdate delete(Long productId, Long version) {
        Product product = lockProduct(productId);
        checkVersion(product, version);
        images.findByProductId(productId).ifPresent(image -> {
            registerCleanup(image.storageKey, null);
            entityManager.lock(product, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
            images.delete(image);
            images.flush();
        });
        return new ImageUpdate(null, product.version);
    }

    private Product lockProduct(Long id) {
        return products.locked(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    }

    private void checkVersion(Product product, Long version) {
        if (version == null || version < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La versión del producto es obligatoria");
        }
        if (!version.equals(product.version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La versión del producto está desactualizada");
        }
    }

    private void registerCleanup(String oldKey, String newKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteSafely(oldKey);
                } else if (status == STATUS_ROLLED_BACK) {
                    deleteSafely(newKey);
                } else {
                    LOG.warn("Unknown image transaction outcome. Retained keys {} and {} for reconciliation",
                            oldKey, newKey);
                }
            }
        });
    }

    private void deleteSafely(String key) {
        if (key != null) {
            try {
                storage.delete(key);
            } catch (IOException | RuntimeException failure) {
                LOG.warn("Unable to remove unreferenced product image {}", key, failure);
            }
        }
    }

    private ResponseStatusException storageError(IOException failure) {
        LOG.warn("Product image storage operation failed", failure);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El almacenamiento de imágenes no está disponible");
    }
}
