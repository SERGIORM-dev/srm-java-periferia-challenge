CREATE TABLE product_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sku VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
    name VARCHAR(150) NOT NULL,
    margin_percentage DECIMAL(7, 2) NOT NULL,
    preferred_provider VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_product_rules PRIMARY KEY (id),
    CONSTRAINT uk_product_rules_sku UNIQUE (sku),
    CONSTRAINT chk_product_rules_sku CHECK (
        CHAR_LENGTH(sku) > 0
        AND NOT REGEXP_LIKE(sku, '^[[:space:]]|[[:space:]]$')
        AND sku = UPPER(sku)
    ),
    CONSTRAINT chk_product_rules_name CHECK (
        REGEXP_LIKE(name, '[^[:space:]]')
    ),
    CONSTRAINT chk_product_rules_margin CHECK (margin_percentage >= 0),
    CONSTRAINT chk_product_rules_provider CHECK (
        preferred_provider IN ('AUTO', 'PROVIDER_A', 'PROVIDER_B')
    ),
    CONSTRAINT chk_product_rules_active CHECK (active IN (FALSE, TRUE))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_as_cs;
