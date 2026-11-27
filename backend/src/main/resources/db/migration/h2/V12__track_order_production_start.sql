ALTER TABLE customer_orders ADD COLUMN production_started_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE customer_orders ADD COLUMN production_started_by VARCHAR(255);
ALTER TABLE customer_orders ADD CONSTRAINT ck_customer_order_production_start
    CHECK ((production_started_at IS NULL AND production_started_by IS NULL)
        OR (production_started_at IS NOT NULL AND production_started_by IS NOT NULL));
