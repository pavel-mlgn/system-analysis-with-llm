--liquibase formatted sql

--changeset developer:001-create-tables

CREATE TABLE blocked_senders (
    id              BIGSERIAL PRIMARY KEY,
    sender_id       BIGINT NOT NULL UNIQUE,
    reason          VARCHAR(255) NOT NULL,
    blocked_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_blocked_senders_sender_id ON blocked_senders(sender_id);

CREATE TABLE screening_checks (
    transfer_id  BIGINT PRIMARY KEY,
    sender_id    BIGINT NOT NULL,
    result       VARCHAR(10) NOT NULL,
    reason       VARCHAR(50),
    checked_at   TIMESTAMP NOT NULL
);

CREATE INDEX idx_screening_checks_sender_id ON screening_checks(sender_id);
