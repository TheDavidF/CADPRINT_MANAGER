CREATE TABLE production_price_estimates (
    calculation_id BIGINT PRIMARY KEY,
    target_margin_pct DECIMAL(7, 4) NOT NULL,
    suggested_unit_price DECIMAL(27, 2) NOT NULL,
    chosen_unit_price DECIMAL(27, 2) NOT NULL,
    gross_profit_per_unit DECIMAL(27, 2) NOT NULL,
    actual_margin_pct DECIMAL(27, 4),
    below_cost BOOLEAN NOT NULL,
    CONSTRAINT fk_production_price_estimate_calc FOREIGN KEY (calculation_id) REFERENCES production_calculations(id),
    CONSTRAINT ck_production_price_target_margin CHECK (target_margin_pct >= 0 AND target_margin_pct < 100),
    CONSTRAINT ck_production_price_values CHECK (suggested_unit_price >= 0 AND chosen_unit_price >= 0),
    CONSTRAINT ck_production_price_actual_margin CHECK (
        (chosen_unit_price = 0 AND actual_margin_pct IS NULL)
        OR (chosen_unit_price > 0 AND actual_margin_pct IS NOT NULL)
    )
);
