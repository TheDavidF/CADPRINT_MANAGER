CREATE TABLE customer_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    CONSTRAINT uq_customer_order_quotation UNIQUE (quotation_id),
    CONSTRAINT fk_customer_order_revision FOREIGN KEY (quotation_id, revision_number)
        REFERENCES quotation_revisions(quotation_id, revision_number)
);
