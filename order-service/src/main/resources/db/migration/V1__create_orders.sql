CREATE TYPE order_status AS ENUM (
    'PENDING',
    'INVENTORY_RESERVED',
    'CONFIRMED',
    'CANCELLED',
    'PAYOUT_TRIGGERED'
);

CREATE TABLE orders (
    id            UUID         PRIMARY KEY,
    order_number  VARCHAR(50)  NOT NULL UNIQUE,
    buyer_id      VARCHAR(255) NOT NULL,
    seller_id     VARCHAR(255),
    status        order_status NOT NULL DEFAULT 'PENDING',
    total_amount  DECIMAL(19, 2) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE order_line_items (
    id          UUID          PRIMARY KEY,
    order_id    UUID          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sku_code    VARCHAR(255)  NOT NULL,
    product_id  UUID,
    quantity    INT           NOT NULL,
    unit_price  DECIMAL(19, 2) NOT NULL
);

CREATE INDEX idx_orders_buyer_id ON orders (buyer_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_order_line_items_order_id ON order_line_items (order_id);
