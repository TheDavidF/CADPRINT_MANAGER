CREATE TABLE product_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    storage_key VARCHAR(36) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_product_images_product UNIQUE (product_id),
    CONSTRAINT uq_product_images_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_product_images_content_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_product_images_file_size CHECK (file_size BETWEEN 1 AND 5242880)
);
