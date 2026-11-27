CREATE TABLE product_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_product_categories_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_product_categories_key CHECK (BINARY normalized_name = BINARY LOWER(TRIM(name)))
);

CREATE INDEX idx_product_categories_active ON product_categories(active, name);

CREATE TABLE catalog_skus (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE,
    kind VARCHAR(7) NOT NULL,
    CONSTRAINT uq_catalog_skus_id_kind UNIQUE (id, kind),
    CONSTRAINT ck_catalog_skus_kind CHECK (kind IN ('PRODUCT', 'VARIANT')),
    CONSTRAINT ck_catalog_skus_value CHECK (CHAR_LENGTH(TRIM(sku)) > 0 AND BINARY sku = BINARY UPPER(TRIM(sku)))
);

CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku_id BIGINT NOT NULL UNIQUE,
    sku_kind VARCHAR(7) NOT NULL DEFAULT 'PRODUCT',
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    category_id BIGINT NOT NULL,
    production_mode VARCHAR(20) NOT NULL,
    pricing_mode VARCHAR(24) NOT NULL,
    base_price DECIMAL(16, 2),
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES product_categories(id),
    CONSTRAINT fk_products_catalog_sku FOREIGN KEY (sku_id, sku_kind) REFERENCES catalog_skus(id, kind),
    CONSTRAINT ck_products_sku_kind CHECK (sku_kind = 'PRODUCT'),
    CONSTRAINT ck_products_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_products_production_mode CHECK (production_mode IN ('BAJO_PEDIDO', 'PARA_EXISTENCIAS')),
    CONSTRAINT ck_products_pricing_mode CHECK (pricing_mode IN ('PRECIO_BASE', 'REQUIERE_COTIZACION')),
    CONSTRAINT ck_products_base_price CHECK (
        (base_price IS NULL AND pricing_mode = 'REQUIERE_COTIZACION') OR
        (base_price IS NOT NULL AND base_price > 0)
    )
);

CREATE INDEX idx_products_category_active ON products(category_id, active);
CREATE INDEX idx_products_active ON products(active);

CREATE TABLE product_variants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL UNIQUE,
    sku_kind VARCHAR(7) NOT NULL DEFAULT 'VARIANT',
    name VARCHAR(255) NOT NULL,
    size VARCHAR(255),
    color VARCHAR(255),
    presentation VARCHAR(255),
    own_price DECIMAL(16, 2),
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_product_variants_catalog_sku FOREIGN KEY (sku_id, sku_kind) REFERENCES catalog_skus(id, kind),
    CONSTRAINT ck_product_variants_sku_kind CHECK (sku_kind = 'VARIANT'),
    CONSTRAINT ck_product_variants_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_product_variants_own_price CHECK (own_price IS NULL OR own_price > 0)
);

CREATE INDEX idx_product_variants_product_active ON product_variants(product_id, active);
