ALTER TABLE customer_orders ADD COLUMN costs_confirmed_at DATETIME(6);
ALTER TABLE customer_orders ADD COLUMN costs_confirmed_by VARCHAR(255);
ALTER TABLE customer_orders ADD CONSTRAINT ck_customer_order_cost_confirmation
    CHECK ((costs_confirmed_at IS NULL AND costs_confirmed_by IS NULL)
        OR (costs_confirmed_at IS NOT NULL AND costs_confirmed_by IS NOT NULL));
