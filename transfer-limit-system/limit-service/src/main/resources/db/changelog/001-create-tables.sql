--liquibase formatted sql

--changeset developer:001-create-tables

CREATE TABLE sender_usage (
    sender_id           BIGINT PRIMARY KEY,
    daily_used          DECIMAL(15,2) NOT NULL DEFAULT 0,
    monthly_used        DECIMAL(15,2) NOT NULL DEFAULT 0,
    last_daily_reset    DATE NOT NULL DEFAULT CURRENT_DATE,
    last_monthly_reset  DATE NOT NULL DEFAULT CURRENT_DATE,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE reservations (
    transfer_id  BIGINT PRIMARY KEY,
    sender_id    BIGINT NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    decision     VARCHAR(10) NOT NULL,
    reason       VARCHAR(50),
    state        VARCHAR(20) NOT NULL,
    reserved_on  DATE NOT NULL,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL
);

CREATE INDEX idx_reservations_sender_id ON reservations(sender_id);
