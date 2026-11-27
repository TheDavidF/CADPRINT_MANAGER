CREATE TABLE production_consumptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_order_id BIGINT NOT NULL,
    movement_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    CONSTRAINT uq_production_consumption_request UNIQUE (customer_order_id, request_id),
    CONSTRAINT uq_production_consumption_movement UNIQUE (movement_id),
    CONSTRAINT fk_production_consumption_order FOREIGN KEY (customer_order_id) REFERENCES customer_orders(id),
    CONSTRAINT fk_production_consumption_movement FOREIGN KEY (movement_id) REFERENCES movements(id)
);
