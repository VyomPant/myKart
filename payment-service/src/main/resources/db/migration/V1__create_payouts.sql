CREATE TYPE payout_status AS ENUM ('PENDING', 'IN_PROGRESS', 'SUCCESS', 'FAILED');
CREATE TYPE payment_channel AS ENUM ('UPI', 'IMPS', 'NEFT');
CREATE TYPE failure_type AS ENUM ('TRANSIENT', 'PERMANENT');

CREATE TABLE payouts (
    id                    UUID         PRIMARY KEY,
    order_id              VARCHAR(255) NOT NULL,
    seller_id             VARCHAR(255) NOT NULL,
    amount                NUMERIC(19, 2) NOT NULL,
    account_number        VARCHAR(50)  NOT NULL,
    ifsc_code             VARCHAR(20)  NOT NULL,
    beneficiary_name      VARCHAR(255) NOT NULL,
    channel               payment_channel,
    status                payout_status NOT NULL DEFAULT 'PENDING',
    external_reference_id VARCHAR(255),
    failure_type          failure_type,
    failure_message       TEXT,
    retry_count           INT          NOT NULL DEFAULT 0,
    max_retries           INT          NOT NULL DEFAULT 5,
    last_attempt_at       TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version               BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_payout_order_id UNIQUE (order_id)
);

CREATE INDEX idx_payouts_status ON payouts (status);
CREATE INDEX idx_payouts_seller_id ON payouts (seller_id);
CREATE INDEX idx_payouts_pending_retry ON payouts (created_at)
    WHERE status = 'PENDING'
       OR (status = 'FAILED' AND failure_type = 'TRANSIENT');
