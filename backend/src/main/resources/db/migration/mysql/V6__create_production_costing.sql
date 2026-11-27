CREATE TABLE production_cost_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    electricity_rate_per_kwh DECIMAL(27, 8) NOT NULL,
    labor_rate_per_hour DECIMAL(27, 8) NOT NULL,
    indirect_rate_per_hour DECIMAL(27, 8) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_production_cost_profiles_code UNIQUE (code),
    CONSTRAINT ck_production_profiles_text CHECK (
        CHAR_LENGTH(TRIM(code)) > 0 AND CHAR_LENGTH(TRIM(name)) > 0 AND CHAR_LENGTH(TRIM(updated_by)) > 0
    ),
    CONSTRAINT ck_production_profiles_currency CHECK (currency = 'GTQ'),
    CONSTRAINT ck_production_profiles_rates CHECK (
        electricity_rate_per_kwh >= 0 AND labor_rate_per_hour >= 0 AND indirect_rate_per_hour >= 0
    )
);

CREATE INDEX idx_production_profiles_active ON production_cost_profiles(active, code);

CREATE TABLE production_machines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    technique VARCHAR(16) NOT NULL,
    nominal_power_watts DECIMAL(19, 6),
    machine_rate_per_hour DECIMAL(27, 8) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_production_machines_code UNIQUE (code),
    CONSTRAINT uq_production_machines_id_technique UNIQUE (id, technique),
    CONSTRAINT ck_production_machines_text CHECK (
        CHAR_LENGTH(TRIM(code)) > 0 AND CHAR_LENGTH(TRIM(name)) > 0 AND CHAR_LENGTH(TRIM(updated_by)) > 0
    ),
    CONSTRAINT ck_production_machines_technique CHECK (technique IN ('FDM', 'SUBLIMATION')),
    CONSTRAINT ck_production_machines_rates CHECK (
        machine_rate_per_hour >= 0 AND (nominal_power_watts IS NULL OR nominal_power_watts > 0)
    )
);

CREATE INDEX idx_production_machines_active ON production_machines(technique, active, code);

CREATE TABLE production_calculations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    technique VARCHAR(16) NOT NULL,
    formula_version VARCHAR(64) NOT NULL,
    username VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    job_description VARCHAR(1000) NOT NULL,
    expected_good_units BIGINT NOT NULL,
    product_id BIGINT,
    variant_id BIGINT,
    cost_profile_id BIGINT,
    machine_id BIGINT,
    supersedes_id BIGINT,
    commercial_name VARCHAR(255),
    commercial_sku VARCHAR(255),
    currency VARCHAR(3) NOT NULL,
    productive_seconds BIGINT NOT NULL,
    indirect_rate_per_hour DECIMAL(27, 8) NOT NULL,
    indirect_cost DECIMAL(19, 2) NOT NULL,
    total_cost DECIMAL(19, 2) NOT NULL,
    unit_cost DECIMAL(27, 8) NOT NULL,
    CONSTRAINT uq_production_calculations_id_technique UNIQUE (id, technique),
    CONSTRAINT fk_production_calc_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_production_calc_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    CONSTRAINT fk_production_calc_profile FOREIGN KEY (cost_profile_id) REFERENCES production_cost_profiles(id),
    CONSTRAINT fk_production_calc_machine FOREIGN KEY (machine_id, technique) REFERENCES production_machines(id, technique),
    CONSTRAINT fk_production_calc_previous FOREIGN KEY (supersedes_id) REFERENCES production_calculations(id),
    CONSTRAINT ck_production_calc_technique CHECK (technique IN ('FDM', 'SUBLIMATION')),
    CONSTRAINT ck_production_calc_currency CHECK (currency = 'GTQ'),
    CONSTRAINT ck_production_calc_subject CHECK (product_id IS NULL OR variant_id IS NULL),
    CONSTRAINT ck_production_calc_subject_snapshot CHECK (
        (product_id IS NULL AND variant_id IS NULL) OR
        (commercial_name IS NOT NULL AND CHAR_LENGTH(TRIM(commercial_name)) > 0
            AND commercial_sku IS NOT NULL AND CHAR_LENGTH(TRIM(commercial_sku)) > 0)
    ),
    CONSTRAINT ck_production_calc_text CHECK (
        CHAR_LENGTH(TRIM(formula_version)) > 0 AND CHAR_LENGTH(TRIM(username)) > 0
            AND CHAR_LENGTH(TRIM(job_description)) > 0
    ),
    CONSTRAINT ck_production_calc_amounts CHECK (
        expected_good_units > 0 AND productive_seconds >= 0 AND indirect_rate_per_hour >= 0
            AND indirect_cost >= 0 AND total_cost >= indirect_cost AND unit_cost >= 0
    )
);

CREATE INDEX idx_production_calc_technique_date ON production_calculations(technique, created_at, id);
CREATE INDEX idx_production_calc_product_date ON production_calculations(product_id, created_at, id);
CREATE INDEX idx_production_calc_variant_date ON production_calculations(variant_id, created_at, id);
CREATE INDEX idx_production_calc_machine ON production_calculations(machine_id, technique);

CREATE TABLE production_fdm_details (
    calculation_id BIGINT PRIMARY KEY,
    technique VARCHAR(16) DEFAULT 'FDM' NOT NULL,
    print_seconds BIGINT NOT NULL,
    labor_seconds BIGINT NOT NULL,
    part_mass_grams DECIMAL(19, 6) NOT NULL,
    support_mass_grams DECIMAL(19, 6),
    CONSTRAINT fk_production_fdm_calculation FOREIGN KEY (calculation_id, technique)
        REFERENCES production_calculations(id, technique),
    CONSTRAINT ck_production_fdm_technique CHECK (technique = 'FDM'),
    CONSTRAINT ck_production_fdm_values CHECK (
        print_seconds > 0 AND labor_seconds >= 0 AND part_mass_grams > 0
            AND (support_mass_grams IS NULL OR support_mass_grams >= 0)
    )
);

CREATE TABLE production_sublimation_details (
    calculation_id BIGINT PRIMARY KEY,
    technique VARCHAR(16) DEFAULT 'SUBLIMATION' NOT NULL,
    cycle_count BIGINT NOT NULL,
    press_seconds_per_cycle BIGINT NOT NULL,
    preparation_seconds BIGINT NOT NULL,
    labor_seconds BIGINT NOT NULL,
    CONSTRAINT fk_production_sublimation_calculation FOREIGN KEY (calculation_id, technique)
        REFERENCES production_calculations(id, technique),
    CONSTRAINT ck_production_sublimation_technique CHECK (technique = 'SUBLIMATION'),
    CONSTRAINT ck_production_sublimation_values CHECK (
        cycle_count > 0 AND press_seconds_per_cycle > 0 AND preparation_seconds >= 0 AND labor_seconds >= 0
    )
);

CREATE TABLE production_cost_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    calculation_id BIGINT NOT NULL,
    position INTEGER NOT NULL,
    cost_type VARCHAR(16) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    quantity DECIMAL(19, 6) NOT NULL,
    unit VARCHAR(64) NOT NULL,
    applied_rate DECIMAL(27, 8) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    material_id BIGINT,
    cost_source VARCHAR(24) NOT NULL,
    material_sku VARCHAR(255),
    material_name VARCHAR(255),
    material_unit VARCHAR(255),
    conversion_factor DECIMAL(20, 9),
    CONSTRAINT fk_production_cost_line_calc FOREIGN KEY (calculation_id) REFERENCES production_calculations(id),
    CONSTRAINT fk_production_cost_line_material FOREIGN KEY (material_id) REFERENCES materials(id),
    CONSTRAINT uq_production_cost_line_position UNIQUE (calculation_id, position),
    CONSTRAINT ck_production_cost_line_type CHECK (
        cost_type IN ('MATERIAL', 'ENERGY', 'MACHINE', 'LABOR', 'INDIRECT', 'OTHER')
    ),
    CONSTRAINT ck_production_cost_line_source CHECK (
        cost_source IN ('MATERIAL_REFERENCE', 'COST_PROFILE', 'MACHINE_RATE', 'MANUAL')
    ),
    CONSTRAINT ck_production_cost_line_material CHECK (
        (material_id IS NULL AND cost_source <> 'MATERIAL_REFERENCE') OR (
            material_id IS NOT NULL AND cost_type = 'MATERIAL' AND cost_source = 'MATERIAL_REFERENCE'
            AND material_sku IS NOT NULL AND CHAR_LENGTH(TRIM(material_sku)) > 0
            AND material_name IS NOT NULL AND CHAR_LENGTH(TRIM(material_name)) > 0
            AND material_unit IS NOT NULL AND CHAR_LENGTH(TRIM(material_unit)) > 0
            AND conversion_factor IS NOT NULL
        )
    ),
    CONSTRAINT ck_production_cost_line_values CHECK (
        position > 0 AND quantity > 0 AND applied_rate >= 0 AND amount >= 0
            AND (conversion_factor IS NULL OR conversion_factor > 0)
            AND CHAR_LENGTH(TRIM(unit)) > 0 AND CHAR_LENGTH(TRIM(description)) > 0
    )
);

CREATE INDEX idx_production_cost_line_material ON production_cost_lines(material_id);
