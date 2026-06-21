CREATE TABLE inventory (
    id                UUID        PRIMARY KEY,
    sku_code          VARCHAR(255) NOT NULL UNIQUE,
    product_id        UUID,
    quantity          INT         NOT NULL DEFAULT 0,
    reserved_quantity INT         NOT NULL DEFAULT 0,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_inventory_sku_code ON inventory (sku_code);
CREATE INDEX idx_inventory_product_id ON inventory (product_id);
