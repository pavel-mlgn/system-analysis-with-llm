--liquibase formatted sql

--changeset developer:001-create-tables

CREATE TABLE transfers (
    id              BIGINT PRIMARY KEY,
    sender_id       BIGINT NOT NULL,
    recipient_id    BIGINT NOT NULL,
    amount          DECIMAL(15,2) NOT NULL,
    currency        VARCHAR(10) NOT NULL DEFAULT 'RUB',
    status          VARCHAR(20) NOT NULL,
    decided_by      VARCHAR(20),
    message_info    TEXT,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP
);

CREATE INDEX idx_transfers_sender_id ON transfers(sender_id);
CREATE INDEX idx_transfers_status ON transfers(status);

CREATE TABLE transfer_history (
    id              BIGSERIAL PRIMARY KEY,
    transfer_id     BIGINT NOT NULL REFERENCES transfers(id),
    status          VARCHAR(20) NOT NULL,
    decided_by      VARCHAR(20),
    message_info    TEXT,
    changed_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    stage           VARCHAR(20) NOT NULL,
    CONSTRAINT uq_transfer_history_stage UNIQUE (transfer_id, stage)
);

CREATE INDEX idx_transfer_history_transfer_id ON transfer_history(transfer_id);
