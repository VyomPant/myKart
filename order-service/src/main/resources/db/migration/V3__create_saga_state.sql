CREATE TYPE saga_step AS ENUM (
    'INVENTORY_RESERVE',
    'ORDER_PERSIST',
    'OUTBOX_WRITE',
    'COMPLETED',
    'COMPENSATING',
    'FAILED'
);

CREATE TABLE saga_state (
    order_id       UUID       PRIMARY KEY,
    step           saga_step  NOT NULL,
    failure_reason TEXT,
    updated_at     TIMESTAMPTZ NOT NULL
);
